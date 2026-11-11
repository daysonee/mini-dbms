package ru.daysonee.dbms.storage;

import ru.daysonee.dbms.schema.TableSchema;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;


/**
 * Схема таблицы + файл со строками. Через класс выполняются операции со строками таблицы
 */
public class Table implements AutoCloseable, Iterable<StoredRow>{
    private final TableSchema schema;
    private final Path path;
    private final TableFile file;

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
        return new Table(schema, path, TableFile.create(path, new RowSerialiser(schema)));
    }

    /**
     * Открывает существующую таблицу или создает новую пустую если файла нет
     * @param dir директория базы данных
     * @param schema схема таблицы
     * @return открытая таблицы
     * @throws IOException если файл не получилось создать
     */
    public static Table open(Path dir, TableSchema schema) throws IOException{
        Path path = pathOf(dir, schema);
        if (!Files.exists(path)){
            return create(dir, schema);
        }
        return new Table(schema, path, TableFile.open(path, new RowSerialiser(schema)));
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
    public int insert(Row row) throws IOException{
        return file.insert(row);
    }

        /**
     * Перезаписывает строку
     * @param slot номер ячейки
     * @param row  новая строка
     * @throws IOException при ошибке записи
     */
    public void update(int slot, Row row) throws IOException {
        file.update(slot, row);
    }

    /**
     * Удаляет строку
     * @param slot номер ячейки
     * @throws IOException при ошибке записи
     */
    public void delete(int slot) throws IOException {
        file.delete(slot);
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
    }

    @Override
    public void close() throws IOException {
        file.close();
    }

    /**
     * Закрывает таблицу и удаляет её файл
     *
     * @throws IOException если файл не удалось удалить
     */
    public void closeAndDelete() throws IOException {
        file.close();
        Files.deleteIfExists(path);
    }

    
}
