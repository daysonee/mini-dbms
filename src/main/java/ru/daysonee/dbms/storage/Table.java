package ru.daysonee.dbms.storage;

import ru.daysonee.dbms.schema.IndexInfo;
import ru.daysonee.dbms.schema.TableSchema;
import ru.daysonee.dbms.schema.IndexInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Схема таблицы + файл со строками. Через класс выполняются операции со строками таблицы. Поддерживает индексы
 */
public class Table implements AutoCloseable, Iterable<StoredRow>{
    private final TableSchema schema;
    private final Path path;
    private final TableFile file;
    private final Map<Integer, HashIndex> indexes = new LinkedHashMap<>();

    private Table(TableSchema schema, Path path, TableFile file){
        this.schema = schema;
        this.path = path;
        this.file = file;
    }
    /**
     * Возвращает путь к файлу таблицы
     * @param dir директория базы данных
     * @param schema схема таблицы
     * @return путь до таблицы формата dir/name.tbl
     */
    public static Path pathOf(Path dir, TableSchema schema){
        return dir.resolve(schema.getName().toLowerCase() + ".tbl");
    }

    /**
     * Создает новую таблицу
     * @param dir директория базы данных
     * @param schema схема таблицы
     * @return открытая таблицы
     * @throws IOException если файл не получилось создать
     */
    public static Table create(Path dir, TableSchema schema) throws IOException{
        Path path = pathOf(dir, schema);
        Table t = new Table(schema, path, TableFile.create(path, new RowSerialiser(schema)));
        t.indexes.put(schema.getPrimaryKeyIndex(), HashIndex.create(t.indexPath("pk")));
        return t;
    }

    /**
     * Открывает существующую таблицу или создает новую пустую если файла нет
     * @param dir директория базы данных
     * @param schema схема таблицы
     * @param secondary индексы из CREATE INDEX
     * @param rebuild   перестроить все индексы после сбоя
     * @return открытая таблицы
     * @throws IOException если файл не получилось создать
     */
    public static Table open(Path dir, TableSchema schema, List<IndexInfo> secondary, boolean rebuild) throws IOException{
        Path path = pathOf(dir, schema);
        if (!Files.exists(path)){
            Table t = create(dir, schema);
            for (IndexInfo info : secondary) {
                t.addIndex(info);
            }
            return t;
        }
        Table t = new Table(schema, path, TableFile.open(path, new RowSerialiser(schema)));
        t.openIndex(schema.getPrimaryKeyIndex(), "pk", rebuild);
        for (IndexInfo info : secondary) {
            t.openIndex(schema.indexOf(info.column()), info.name(), rebuild);
        }
        return t;
    }

    /**
     * Возвращает схему таблицы
     * @return схема таблицы
     */
    public TableSchema schema(){
        return schema;
    }

    /**
     * Вставляет строку в таблицу на первое свободное место
     * @param row строка
     * @return ячейка в которой строка
     * @throws IOException при ошибке записи
     */
    public int insert(Row row) throws IOException {
        int slot = file.insert(row);
        for (Map.Entry<Integer, HashIndex> e : indexes.entrySet()) {
            Object value = row.get(e.getKey());
            if (value != null) {
                e.getValue().insert(HashIndex.hashOf(value), slot);
            }
        }
        return slot;
    }

        /**
     * Перезаписывает строку
     * @param slot номер ячейки
     * @param row  новая строка
     * @throws IOException при ошибке записи
     */
    public void update(int slot, Row row) throws IOException {
        Row oldRow = file.read(slot);
        file.update(slot, row);
        for ( Map.Entry<Integer, HashIndex> e : indexes.entrySet()){
            Object before = oldRow.get(e.getKey());
            Object after = row.get(e.getKey());
            if (!Objects.equals(before, after)){
                if (before != null){
                    e.getValue().remove(HashIndex.hashOf(before), slot);
                }
                if (after != null){
                    e.getValue().insert(HashIndex.hashOf(after), slot);
                }
            }

        }
    }

    /**
     * Удаляет строку
     * @param slot номер ячейки
     * @throws IOException при ошибке записи
     */
    public void delete(int slot) throws IOException {
        Row oldRow = file.read(slot);
        file.delete(slot);
        for (Map.Entry<Integer, HashIndex> e : indexes.entrySet()) {
            Object value = oldRow.get(e.getKey());
            if (value != null) {
                e.getValue().remove(HashIndex.hashOf(value), slot);
            }
        }
    }

    /**
     * Возвращает число строк
     * @return число строк
     */
    public int size() {
        return file.getRowCount();
    }

    @Override
    public Iterator<StoredRow> iterator() {
        return file.iterator();
    }

    /**
     * Сбрасывает данные на диск
     * @throws IOException при ошибке записи
     */
    public void flush() throws IOException {
        file.flush();
        for (HashIndex idx : indexes.values()) {
            idx.flush();
        }
    }

    @Override
    public void close() throws IOException {
        file.close();
        file.close();
        for (HashIndex idx : indexes.values()) {
            idx.close();
        }
    }

    /**
     * Закрывает таблицу и удаляет её файл
     *
     * @throws IOException если файл не удалось удалить
     */
    public void closeAndDelete(List<IndexInfo> secondary) throws IOException {
        close();
        Files.deleteIfExists(path);
        Files.deleteIfExists(indexPath("pk"));
        for (IndexInfo info : secondary) {
            Files.deleteIfExists(indexPath(info.name()));
        }
    }

        /**
     * Открывает индекс по колонке или строит его заново, если файла нет или был сбой.
     *
     * @param column  номер колонки
     * @param name    имя индекса
     * @param rebuild перестроить индекс
     * @throws IOException при ошибке чтения или записи
     */
    private void openIndex(int column, String name, boolean rebuild) throws IOException {
        Path p = indexPath(name);
        if (rebuild || !Files.exists(p)) {
            indexes.put(column, buildIndex(column, p));
        } else {
            indexes.put(column, HashIndex.open(p));
        }
    }

    /**
     * Создаёт индекс заново и заполняет его по всем строкам таблицы: O(n).
     *
     * @param column номер колонки
     * @param p      путь к файлу индекса
     * @return заполненный индекс
     * @throws IOException при ошибке записи
     */
    private HashIndex buildIndex(int column, Path p) throws IOException {
        HashIndex idx = HashIndex.create(p);
        for (StoredRow stored : this) {
            Object value = stored.row().get(column);
            if (value != null) {
                idx.insert(HashIndex.hashOf(value), stored.slot());
            }
        }
        return idx;
    }

    /**
     * Создаёт новый индекс по колонке и заполняет его существующими строками
     * @param info описание индекса
     * @throws IOException при ошибке записи
     */
    public void addIndex(IndexInfo info) throws IOException {
        int column = schema.indexOf(info.column());
        indexes.put(column, buildIndex(column, indexPath(info.name())));
    }

    /**
     * Проверяет есть ли индекс по колонке
     * @param column номер колонки
     * @return true или false
     */
    public boolean hasIndex(int column) {
        return indexes.containsKey(column);
    }

    /**
     * Находит строки, у которых значение колонки равно данному, через индекс
     * @param column номер колонки у которой есть индекс
     * @param value  искомое значение
     * @return найденные строки
     * @throws IOException при ошибке чтения
     */
    public List<StoredRow> lookup(int column, Object value) throws IOException {
        List<StoredRow> result = new ArrayList<>();
        if (value == null) {
            return result;
        }
        for (int slot : indexes.get(column).find(HashIndex.hashOf(value))) {
            Row row = file.read(slot);
            if (row != null && Objects.equals(row.get(column), value)) {
                result.add(new StoredRow(slot, row));
            }
        }
        return result;
    }

    /**
     * Находит строку по первичному ключу через индекс
     * @param key значение первичного ключа
     * @return строка или null
     * @throws IOException при ошибке чтения
     */
    public StoredRow findByKey(Object key) throws IOException {
        List<StoredRow> found = lookup(schema.getPrimaryKeyIndex(), key);
        return found.isEmpty() ? null : found.get(0);
    }

    /**
     * Возвращает путь к файлу индекса таблицы
     * @param indexName имя индекса
     * @return путь формата dir/table.indexName.idx
     */
    private Path indexPath(String indexName) {
        return path.resolveSibling(schema.getName().toLowerCase() + "." + indexName.toLowerCase() + ".idx");
    }

}
