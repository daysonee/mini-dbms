package ru.daysonee.dbms.engine;

import ru.daysonee.dbms.DbException;
import ru.daysonee.dbms.schema.Catalog;
import ru.daysonee.dbms.schema.TableSchema;
import ru.daysonee.dbms.storage.Table;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Фасад для доступа к хранилищу, каталог и открытые таблицы в одной папке
 */
public class Database implements AutoCloseable{
    private final Path dir;
    private final Catalog catalog;
    private final Map<String, Table> tables = new LinkedHashMap<>();

    private Database(Path dir, Catalog catalog) {
        this.dir = dir;
        this.catalog = catalog;
    }

    /**
     * Открывает базу данных или создает ее и открывает все ее таблицы
     * @param dir директория базы данных
     * @return базу данных
     * @throws IOException если файлы не получилось прочитать
     */
    public static Database open(Path dir) throws IOException {
        Database db = new Database(dir, Catalog.open(dir));
        for (TableSchema schema : db.catalog.all()){
            db.tables.put(schema.getName().toLowerCase(), Table.open(dir, schema));
        }
        return db;
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
        catalog.drop(name);
        tables.remove(name.toLowerCase()).closeAndDelete();
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
    }
}
