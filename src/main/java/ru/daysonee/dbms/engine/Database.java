package ru.daysonee.dbms.engine;

import ru.daysonee.dbms.DbException;
import ru.daysonee.dbms.schema.Catalog;
import ru.daysonee.dbms.schema.TableSchema;
import ru.daysonee.dbms.storage.Table;
import ru.daysonee.dbms.schema.IndexInfo;

import java.nio.file.Files;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
/**
 * Фасад для доступа к хранилищу, каталог и открытые таблицы в одной папке
 */
public class Database implements AutoCloseable{
    private final Path dir;
    private final Catalog catalog;
    private final Map<String, Table> tables = new LinkedHashMap<>();
    private static final String OPEN_MARKER = "db.open";
    private final boolean recovered;

    private Database(Path dir, Catalog catalog, boolean recovered) {
        this.dir = dir;
        this.catalog = catalog;
        this.recovered = recovered;
    }

    /**
     * Открывает базу данных или создает ее и открывает все ее таблицы
     * @param dir директория базы данных
     * @return базу данных
     * @throws IOException если файлы не получилось прочитать
     */
    public static Database open(Path dir) throws IOException {
        Catalog catalog = Catalog.open(dir);
        Path marker = dir.resolve(OPEN_MARKER);
        boolean crashed = Files.exists(marker);
        Database db = new Database(dir, catalog, crashed);
        for (TableSchema schema : catalog.all()) {
            db.tables.put(schema.getName().toLowerCase(),
            Table.open(dir, schema, catalog.indexesOf(schema.getName()), crashed));
        }
        Files.writeString(marker, "");
        return db;
    }

    /**
    * Возвращает были ли перестроены индексы после аварийного завершения
    * @return true, если было восстановление
    */
    public boolean wasRecovered() {
        return recovered;
    }

    /**
     * Возвращает каталог базы
     * @return каталог
     */
    public Catalog catalog() {
        return catalog;
    }

    /**
     * Возвращает открытую таблицу по имени
     * @param name имя таблицы
     * @return таблица
     * @throws DbException если таблицы нет
     */
    public Table table(String name) {
        Table t = tables.get(name.toLowerCase());
        if (t == null){
            throw new DbException("Таблицы с именем " + name + " не существует");
        }
        return t;
    }

    /**
     * Создет таблицу, добавляет схему в каталог и создает файл
     * @param schema схема новой таблицы
     * @throws DbException если таблица уже есть или схема неверна
     * @throws IOException при ошибке записи
     */
    public void createTable(TableSchema schema) throws IOException {
        catalog.create(schema);
        tables.put(schema.getName().toLowerCase(), Table.create(dir, schema));
    }

    /**
     * Удаляет таблицу, убирает схему из каталога и удаляет файл
     * @param name имя таблицы
     * @throws DbException если таблицы нет или на нее ссылается другая таблица
     * @throws IOException при ошибке записи
     */
    public void dropTable(String name) throws IOException {
        List<IndexInfo> indexes = catalog.indexesOf(name);
        catalog.drop(name);
        tables.remove(name.toLowerCase()).closeAndDelete(indexes);
    }

    /**
     * Сбрасывает все таблицы на диск
     * @throws IOException при ошибке записи
     */
    public void flush() throws IOException {
        for (Table t : tables.values()) {
            t.flush();
        }
    }

    @Override
    public void close() throws IOException {
        for (Table t : tables.values()) {
            t.close();
        }
        tables.clear();
        Files.deleteIfExists(dir.resolve(OPEN_MARKER));
    }
    /**
    * Создает индекс, добавляет его в каталог и строит
    * @param info описание индекса
    * @throws DbException если имя занято, нет таблицы или колонки или индекс по уже есть
    * @throws IOException при ошибке записи
    */
    public void createIndex(IndexInfo info) throws IOException {
        Table table = table(info.table());
        catalog.createIndex(info);
        table.addIndex(info);
    }
}
