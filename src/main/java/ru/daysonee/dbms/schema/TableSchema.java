package ru.daysonee.dbms.schema;

import ru.daysonee.dbms.DbException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**Схема одной таблицы, имена и список колонок */
public class TableSchema {
    private final String name;
    private final List<Column> columns;
    private final Map<String, Integer> indexByName = new HashMap<>();
    private final int primaryKeyIndex;
    /**
     * Создаёт схему таблицы и проверяет ее
     * @param name имя таблицы
     * @param columns колонки таблицы
     * @throws DbException если схема таблицы неверная
     */
    public TableSchema(String name, List<Column> columns){
        this.name = name;
        this.columns = List.copyOf(columns);
        int pk = -1;
        for (int i = 0; i < this.columns.size(); ++i){
            Column c = this.columns.get(i);
            if (indexByName.containsKey(c.name().toLowerCase())){
                throw new DbException("Колонка с именем " + c.name() + " уже существует");
            }
            indexByName.put(c.name().toLowerCase(), i);
            if (c.primaryKey()){
                if (pk != -1){
                    throw new DbException("Больше одного первичного ключа");
                }
                pk = i;
            }
        }
        if (pk == -1) {
            throw new DbException("У таблицы нет первичного ключа");
        }
        if (columns.get(pk).type() != DataType.INT && columns.get(pk).type() != DataType.STRING){
            throw new DbException("Неправильный тип первичного ключа");
        }
        this.primaryKeyIndex = pk;

    }
    /**
     * Возвращает имя таблицы
     * @return имя таблицы
     */
    public String getName() { return name; }
    /**
     * Возвращает список колонок таблицы
     * @return список колонок таблицы
     */
    public List<Column> getColumns() { return columns; }
    /**
     *  Возвращает номер колонки первичного ключа таблицы
     * @return индекс первичного ключа таблицы
     */
    public int getPrimaryKeyIndex() { return primaryKeyIndex; }
    /**
     * Возвращает колонку первичного ключа
     * @return первичный ключ таблицы
     */
    public Column getPrimaryKey() { return columns.get(primaryKeyIndex); }

    /**
     * Номер колонки по имени, без учета регистра.
     * @param columnName имя колонки
     * @return индекс колонки
     * @throws DbException если колонки нет
     */
    public int indexOf(String columnName) {
        Integer i = indexByName.get(columnName.toLowerCase());
        if(i == null){
            throw new DbException("Колонки " + columnName + " нет в таблице");
        }
        return i;
    }
    /**
     * находит имя колонки 
     * @param columnName имя колонки
     * @return данные о колонке
     */
    public Column getColumn(String columnName) {
        return columns.get(indexOf(columnName));
    }
}
