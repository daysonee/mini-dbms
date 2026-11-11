package ru.daysonee.dbms.storage;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Хеш-индекс на диске: хеш значения колонки → номер ячейки в файле таблицы.
 * Открытая адресация с линейным пробированием, удаление через tombstone, удвоение размера при заполнении больше 70%.
 * Поиск возвращает список ячеек. Разные значения с одинаковым хешем отсеивает вызывающий код, сравнивая настоящие строки таблицы.
 */
public class HashIndex implements AutoCloseable {
    private static final int MAGIC = 0x48494458;   // "HIDX"
    private static final int HEADER_SIZE = 16;
    private static final int SLOT_SIZE = 1 + 4 + 4;
    private static final int INITIAL_CAPACITY = 16;
    private static final double MAX_LOAD = 0.7;
    private static final byte EMPTY = 0;
    private static final byte OCCUPIED = 1;
    private static final byte DELETED = 2;

    private final Path path;
    private RandomAccessFile file;
    private int capacity;
    private int size;
    private int tombstones;

    private HashIndex(Path path, RandomAccessFile file) {
        this.path = path;
        this.file = file;
    }

    /**
     * Вычисляет хеш значения
     * @param value значение колонки, не null
     * @return хеш
     */
    public static int hashOf(Object value) {
        int h = Objects.hashCode(value) * 0x9E3779B9;
        return h ^ (h >>> 16);
    }

    /**
     * Создаёт пустой индекс, перезаписывает существующий файл.
     * @param path путь к файлу индекса
     * @return открытый индекс
     * @throws IOException если файл не удалось создать
     */
    public static HashIndex create(Path path) throws IOException {
        return create(path, INITIAL_CAPACITY);
    }

    private static HashIndex create(Path path, int capacity) throws IOException {
        RandomAccessFile f = new RandomAccessFile(path.toFile(), "rw");
        f.setLength(0);
        f.setLength(HEADER_SIZE + (long) capacity * SLOT_SIZE);
        HashIndex idx = new HashIndex(path, f);
        idx.capacity = capacity;
        idx.writeHeader();
        return idx;
    }

    /**
     * Открывает существующий индекс
     * @param path путь к файлу индекса
     * @return открытый индекс
     * @throws IOException если файл не получилось прочитать
     */
    public static HashIndex open(Path path) throws IOException {
        RandomAccessFile f = new RandomAccessFile(path.toFile(), "rw");
        if (f.length() < HEADER_SIZE || f.readInt() != MAGIC) {
            f.close();
            throw new IOException("Файл индекса повреждён: " + path);
        }
        HashIndex idx = new HashIndex(path, f);
        idx.capacity = f.readInt();
        idx.size = f.readInt();
        idx.tombstones = f.readInt();
        return idx;
    }

    private void writeHeader() throws IOException {
        file.seek(0);
        file.writeInt(MAGIC);
        file.writeInt(capacity);
        file.writeInt(size);
        file.writeInt(tombstones);
    }

    private long offset(int i) {
        return HEADER_SIZE + (long) i * SLOT_SIZE;
    }

    /**
     * Добавляет пару хеш -> ячейка
     * @param hash хеш значения
     * @param dataSlot номер ячейки в файле таблицы
     * @throws IOException при ошибке записи
     */
    public void insert(int hash, int dataSlot) throws IOException {
        if (size + tombstones + 1 > capacity * MAX_LOAD) {
            rehash();
        }
        int i = Math.floorMod(hash, capacity);
        while (true) {
            file.seek(offset(i));
            byte state = file.readByte();
            if (state != OCCUPIED) {
                if (state == DELETED) {
                    tombstones--;
                }
                file.seek(offset(i));
                file.writeByte(OCCUPIED);
                file.writeInt(hash);
                file.writeInt(dataSlot);
                size++;
                writeHeader();
                return;
            }
            i = (i + 1) % capacity;
        }
    }

    /**
     * Находит все ячейки, у которых хеш значения совпадает с данным
     * @param hash хеш значения
     * @return номера ячеек
     * @throws IOException при ошибке чтения
     */
    public List<Integer> find(int hash) throws IOException {
        List<Integer> result = new ArrayList<>();
        int i = Math.floorMod(hash, capacity);
        for (int step = 0; step < capacity; ++step) {
            file.seek(offset(i));
            byte state = file.readByte();
            if (state == EMPTY) {
                break;
            }
            int h = file.readInt();
            int dataSlot = file.readInt();
            if (state == OCCUPIED && h == hash) {
                result.add(dataSlot);           
            }
            i = (i + 1) % capacity;
        }
        return result;
    }

    /**
     * Удаляет пару хеш -> ячейка
     * @param hash хеш значения
     * @param dataSlot номер ячейки в файле таблицы
     * @throws IOException при ошибке записи
     */
    public void remove(int hash, int dataSlot) throws IOException {
        int i = Math.floorMod(hash, capacity);
        for (int step = 0; step < capacity; ++step) {
            file.seek(offset(i));
            byte state = file.readByte();
            if (state == EMPTY) {
                return;
            }
            int h = file.readInt();
            int slot = file.readInt();
            if (state == OCCUPIED && h == hash && slot == dataSlot) {
                file.seek(offset(i));
                file.writeByte(DELETED);
                size--;
                tombstones++;
                writeHeader();
                return;
            }
            i = (i + 1) % capacity;
        }
    }

    private void rehash() throws IOException {
        Path tmp = Path.of(path + ".tmp");
        HashIndex bigger = create(tmp, capacity * 2);
        for (int i = 0; i < capacity; i++) {
            file.seek(offset(i));
            byte state = file.readByte();
            int h = file.readInt();
            int dataSlot = file.readInt();
            if (state == OCCUPIED) {
                bigger.insert(h, dataSlot);
            }
        }
        file.close();
        bigger.close();
        Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
        file = new RandomAccessFile(path.toFile(), "rw");
        capacity = bigger.capacity;
        size = bigger.size;
        tombstones = 0;
    }

    /**
     * Возвращает число записей в индексе
     * @return число пар хеш -> ячейка
     */
    public int size() {
        return size;
    }

    /**
     * Сбрасывает данные на диск
     * @throws IOException при ошибке записи
     */
    public void flush() throws IOException {
        file.getChannel().force(true);
    }

    @Override
    public void close() throws IOException {
        file.close();
    }
}