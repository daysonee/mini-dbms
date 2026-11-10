package ru.daysonee.dbms.storage;

import ru.daysonee.dbms.DbException;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Файл таблицы: заголовок и ячейки фиксированного размера.
 * Удалённые ячейки связаны в список свободных и переиспользуются при вставке.
 * Формат ячейки: байт статуса (1 — занята, 0 — свободна), затем строка из
 * {@link RowSerialiser}.
 * В свободной ячейке сразу после статуса лежит номер следующей свободной
 * ячейки.
 */
public class TableFile implements AutoCloseable, Iterable<StoredRow> {
    private static final int MAGIC = 0x54424C46;
    private static final int HEADER_SIZE = 20;
    private static final byte FREE = 0;
    private static final byte USED = 1;

    private final RandomAccessFile file;
    private final RowSerialiser serialiser;
    private final int cellSize;
    private int slotCount;
    private int rowCount;
    private int freeHead;

    private TableFile(RandomAccessFile file, RowSerialiser serialiser) {
        this.file = file;
        this.serialiser = serialiser;
        this.cellSize = 1 + serialiser.size();
    }

    /**
     * Записывает заголовок таблицы
     * @throws IOException
     */
    private void writeHeader() throws IOException {
        file.seek(0);
        file.writeInt(MAGIC);
        file.writeInt(cellSize);
        file.writeInt(slotCount);
        file.writeInt(rowCount);
        file.writeInt(freeHead);
    }

    /**
     * Создает пустой файл таблицы или перезаписывает существующий файл
     *
     * @param path       путь к файлу
     * @param serialiser сериализатор строк этой таблицы
     * @return открытый файл таблицы
     * @throws IOException если файл не удалось создать
     */
    public static TableFile create(Path path, RowSerialiser serialiser) throws IOException {
        RandomAccessFile f = new RandomAccessFile(path.toFile(), "rw");
        f.setLength(0);
        TableFile t = new TableFile(f, serialiser);
        t.slotCount = 0;
        t.rowCount = 0;
        t.freeHead = -1;
        t.writeHeader();
        return t;
    }

    /**
     * Открывает существующий файл таблицы
     *
     * @param path путь к файлу
     * @param serialiser сериализатор строк этой таблицы
     * @return открытый файл таблицы
     * @throws IOException если файл не удалось прочитать, он поврежден или не соответствует схеме
     */
    public static TableFile open(Path path, RowSerialiser serialiser) throws IOException {
        RandomAccessFile f = new RandomAccessFile(path.toFile(), "rw");
        TableFile t = new TableFile(f, serialiser);
        f.seek(0);
        if (f.length() < HEADER_SIZE || f.readInt() != MAGIC) {
            f.close();
            throw new IOException("Файл таблицы повреждён: " + path);
        }
        if (f.readInt() != t.cellSize) {
            f.close();
            throw new IOException("Файл таблицы не соответствует схеме: " + path);
        }
        t.slotCount = f.readInt();
        t.rowCount = f.readInt();
        t.freeHead = f.readInt();
        return t;
    }

    private long offset(int slot) {
        return HEADER_SIZE + (long) slot * cellSize;
    }

    /**
     * Добавляет строку в свободную ячейку или в конец файла
     *
     * @param row строка
     * @return номер ячейки, в которую записана строка
     * @throws IOException при ошибке записи
     */
    public int insert(Row row) throws IOException {
        int slot;
        if (freeHead != -1) {
            slot = freeHead;
            file.seek(offset(slot) + 1);
            freeHead = file.readInt();
        } else {
            slot = slotCount;
            slotCount++;
        }
        byte[] cell = new byte[cellSize];
        cell[0] = USED;
        byte[] data = serialiser.toBytes(row);
        System.arraycopy(data, 0, cell, 1, data.length);
        file.seek(offset(slot));
        file.write(cell);
        rowCount++;
        writeHeader();
        return slot;
    }

    /**
     * Читает строку из ячейки
     *
     * @param slot номер ячейки
     * @return строка или null, если ячейка свободна
     * @throws IOException при ошибке чтения
     * @throws DbException если такой ячейки нет
     */
    public Row read(int slot) throws IOException {
        checkSlot(slot);
        byte[] cell = new byte[cellSize];
        file.seek(offset(slot));
        file.readFully(cell);
        if (cell[0] == FREE) {
            return null;
        }
        byte[] data = new byte[cellSize - 1];
        System.arraycopy(cell, 1, data, 0, data.length);
        return serialiser.fromBytes(data);
    }

    /**
     * Перезаписывает строку в занятой ячейке
     *
     * @param slot номер ячейки
     * @param row  новая строка
     * @throws IOException при ошибке записи
     * @throws DbException если ячейки нет или она свободна
     */
    public void update(int slot, Row row) throws IOException {
        checkUsed(slot);
        file.seek(offset(slot) +1);
        file.write(serialiser.toBytes(row));
    }

    /**
     * Удаляет строку
     *
     * @param slot номер ячейки
     * @throws IOException при ошибке записи
     * @throws DbException если ячейки нет или она уже свободна
     */
    public void delete(int slot) throws IOException {
        checkUsed(slot);
        file.seek(offset(slot));
        file.writeByte(FREE);
        file.writeInt(freeHead);
        freeHead = slot;
        rowCount--;
        writeHeader();
    }
    /**
     * Проверяет существуюет ли слот
     * @param slot индекс ячейки
     */
    private void checkSlot(int slot) {
        if (slot < 0 || slot >= slotCount) {
            throw new DbException("Нет ячейки " + slot);
        }
    }
    /**
     * Полверяет занят ли слот
     * @param slot индекс ячейки
     * @throws DbException если слот пуст
     */
    private void checkUsed(int slot) throws IOException {
        checkSlot(slot);
        file.seek(offset(slot));
        if (file.readByte() != USED) {
            throw new DbException("Ячейка " + slot + " пуста");
        }
    }

    /**
     * Возвращает число строк в таблице
     *
     * @return число занятых ячеек
     */
    public int getRowCount() {
        return rowCount;
    }

    /**
     * Возвращает число ячеек в файле вместе со сводобными
     *
     * @return число ячеек
     */
    public int getSlotCount() {
        return slotCount;
    }

    /**
     * Сбрасывает данные файла на диск
     *
     * @throws IOException при ошибке записи
     */
    public void flush() throws IOException {
        file.getChannel().force(true);
    }

    @Override
    public void close() throws IOException {
        file.close();
    }

    /**
     * Возвращает итератор по занятым ячейкам в порядке их номеров
     *
     * @throws DbException если происходят ошибки чтения файла
     * @return итератор строк таблицы
     */
    @Override
    public Iterator<StoredRow> iterator() {
        return new Iterator<>() {
            private int nextSlot = 0;
            private StoredRow ahead = findNext();

            private StoredRow findNext() {
                try {
                    while (nextSlot < slotCount) {
                        int slot = nextSlot++;
                        Row row = read(slot);
                        if (row != null) {
                            return new StoredRow(slot, row);
                        }
                    }
                    return null;
                } catch (IOException e) {
                    throw new DbException("Ошибка чтения таблицы: " + e.getMessage());
                }
            }

            @Override
            public boolean hasNext() {
                return ahead != null;
            }

            @Override
            public StoredRow next() {
                if (ahead == null) {
                    throw new NoSuchElementException();
                }
                StoredRow current = ahead;
                ahead = findNext();
                return current;
            }
        };
    }
}