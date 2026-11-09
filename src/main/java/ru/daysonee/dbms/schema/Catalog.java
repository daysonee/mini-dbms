package ru.daysonee.dbms.schema;

import ru.daysonee.dbms.DbException;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Каталог - схемы всех таблиц базы, хранится в файле catalog.bin. */
public class Catalog {
    private static final int MAGIC = 0x43544C47;

    private final Path file;
    private final Path tmp;
    private final Map<String, TableSchema> tables = new LinkedHashMap<>();

    private Catalog(Path dir){
        this.file = dir.resolve("catalog.bin");
        this.tmp = dir.resolve("catalog.tmp");
    }
    /**
     * Открывает каталог в папке базы данных, если если файла нет, то создает
     * @param dir путь до папки базы данных
     * @return объект класса Catalog
     * @throws IOException
     */
    public static Catalog open(Path dir) throws IOException{
        Files.createDirectories(dir);
        Catalog c = new Catalog(dir);
        Files.deleteIfExists(c.tmp);
        if (Files.exists(c.file)){
            c.load();
        } else {
            c.save();
        }
        return c;
    }

    /**
     * Проверяет существует ли таблица в каталоге
     * @param table название таблицы
     * @return true или false
     */
    public boolean exists(String table) {
        return tables.containsKey(table.toLowerCase());
    }

    /**
     * Возвращает таблицу по имени
     * @param table
     * @return таблицу
     * @throws DbException если таблицы не существует
     */
    public TableSchema get(String table) {

        TableSchema t = tables.get(table.toLowerCase());
        if (t == null){
            throw new DbException("Не существует таблицы с именем: " + table);
        }
        return t;
    }
    /**
     *
     * @return все таблицы в каталоге
     */
    public Collection<TableSchema> all() {
        return Collections.unmodifiableCollection(tables.values());
    }

    /**
     * Добавляет таблицу и сохраняет в каталог
     *
     * @throws DbException если таблица есть или неверный внешний клюс
     */
    public void create(TableSchema schema) throws IOException{
        if (exists(schema.getName())){
            throw new DbException("Таблица с таким именем уже существует");
        }
        for (Column c : schema.getColumns()) {
            if (!c.isForeignKey()) {
                continue;
            }
            TableSchema ref = get(c.refTable());
            Column target = get(c.refColumn());
        }

    }
}
