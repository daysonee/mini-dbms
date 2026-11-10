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
     * @throws IOException если файл каталога не получилось прочитать или создать
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
     * Возвращает схемы всех таблиц каталога
     * @return все таблицы в каталоге
     */
    public Collection<TableSchema> all() {
        return Collections.unmodifiableCollection(tables.values());
    }

    /**
     * Добавляет таблицу и сохраняет в каталог
     * @param schema схема таблицы
     * @throws DbException если таблица есть или неверный внешний клюс
     * @throws IOException если каталог не удалось сохранить
     */
    public void create(TableSchema schema) throws IOException{
        if (exists(schema.getName())){
            throw new DbException("Таблица с именем " + schema.getName() + " уже существует");
        }
        for (Column c : schema.getColumns()) {
            if (!c.isForeignKey()) {
                continue;
            }
            TableSchema ref = get(c.refTable());
            Column target = ref.getColumn(c.refColumn());
            if (!target.primaryKey()){
                throw new DbException("Внешний ключ " + c.name() + " должен ссылаться на первичный ключ");
            }
            if (target.type() != c.type()) {
                throw new DbException("Тип " + c.name() + " не совпадает с типом " + c.refTable() + "." + c.refColumn());
            }
        }
        tables.put(schema.getName().toLowerCase(), schema);
        save();
    }
     /**
      * Удаляет таблицу из каталога если она существует
      * @param tableName ися удаляемой таблицы
      * @throws DbException если на таблицу ссылается другая таблица или ее нет
      * @throws IOException если каталог не удалось сохранить
      */
    public void drop(String tableName) throws IOException{
        TableSchema table = get(tableName);
        for (TableSchema t : tables.values()){
            for ( Column c : t.getColumns()){
                if (c.isForeignKey() && c.refTable().equalsIgnoreCase(tableName)){
                    throw new DbException("Таблица " + t.getName() + " ссылается на таблицу " + tableName);
                }
            }
        }
        tables.remove(tableName.toLowerCase());
        save();
    }
    /** Записывает каталог во временный файл и атомарно заменяет им основной */
    private void save() throws IOException{
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(tmp)))){
            out.writeInt(MAGIC);
            out.writeInt(tables.size());
            for (TableSchema t : tables.values()){
                out.writeUTF(t.getName());
                out.writeInt(t.getColumns().size());
                for (Column c : t.getColumns()){
                    out.writeUTF(c.name());
                    out.writeByte(c.type().ordinal());
                    out.writeInt(c.length());
                    out.writeBoolean(c.primaryKey());
                    out.writeBoolean(c.notNull());
                    out.writeBoolean(c.isForeignKey());
                    if (c.isForeignKey()){
                        out.writeUTF(c.refTable());
                        out.writeUTF(c.refColumn());
                    }
                }
            }
        }
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
    /** Читает каталог из файла */
    private void load() throws IOException{
        try(DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))){
            if (in.readInt() != MAGIC) {
                throw new IOException("Файл каталога повреждён");
            }
            int tableCount = in.readInt();
            for (int i = 0; i < tableCount; ++i){
                String tableName = in.readUTF();
                int columnCount = in.readInt();
                List<Column> columns = new ArrayList<>();
                for (int j = 0; j < columnCount; ++j){
                    String name = in.readUTF();
                    DataType type = DataType.values()[in.readByte()];
                    int length = in.readInt();
                    boolean primaryKey = in.readBoolean();
                    boolean notNull = in.readBoolean();
                    boolean isForeignKey = in.readBoolean();
                    String refTable = null;
                    String refColumn = null;
                    if (isForeignKey){
                        refTable = in.readUTF();
                        refColumn = in.readUTF();
                    }

                    columns.add(new Column(name, type, length, primaryKey, notNull, refTable, refColumn));
                }
                tables.put(tableName.toLowerCase(), new TableSchema(tableName, columns));
            }
        }
    }
}
