package ru.daysonee.dbms.engine;

import ru.daysonee.dbms.DbException;
import ru.daysonee.dbms.schema.Column;
import ru.daysonee.dbms.schema.TableSchema;
import ru.daysonee.dbms.sql.Condition;
import ru.daysonee.dbms.sql.Operator;
import ru.daysonee.dbms.sql.Parser;
import ru.daysonee.dbms.sql.statements.*;
import ru.daysonee.dbms.storage.Row;
import ru.daysonee.dbms.storage.StoredRow;
import ru.daysonee.dbms.storage.Table;
import ru.daysonee.dbms.schema.IndexInfo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/** Выполняет sql запросы над базой данных */
public class QueryExecutor {
    private final Database db;

    /**
     * Создает исполнителя запросов для базы данных
     *
     * @param db открытая база данных
     */
    public QueryExecutor(Database db) {
        this.db = db;
    }

    /**
     * Разбирает и выполняет запрос
     *
     * @param sql текст запроса
     * @return результат выполнения
     * @throws DbException если запрос неправильный
     * @throws IOException при ошибке работы с файлами
     */
    public QueryResult execute(String sql) throws IOException {
        return execute(Parser.parse(sql));
    }

    /**
     * Выполняет разобранный запрос
     *
     * @param statement запрос
     * @return результат выполнения
     * @throws DbException если запрос неправильный
     * @throws IOException при ошибке работы с файлами
     */
    public QueryResult execute(Statement statement) throws IOException {
        return switch (statement) {
            case CreateTableStatement s -> createTable(s);
            case DropTableStatement s -> dropTable(s);
            case CreateIndexStatement s -> createIndex(s);
            case InsertStatement s -> insert(s);
            case SelectStatement s -> select(s);
            case UpdateStatement s -> update(s);
            case DeleteStatement s -> delete(s);
        };
    }

    private QueryResult createIndex(CreateIndexStatement s) throws IOException {
        db.createIndex(new IndexInfo(s.name(), s.table(), s.column()));
        return QueryResult.message("Индекс " + s.name() + " создан");
    }

    // CREATE || DROP

    /**
     * Создает таблицу по sql запросу
     *
     * @param s данные таблицы для создания
     * @return сообщение о создании таблицы
     * @throws IOException
     */
    private QueryResult createTable(CreateTableStatement s) throws IOException {
        db.createTable(new TableSchema(s.table(), s.columns()));
        return QueryResult.message("Таблица " + s.table() + " создана");
    }

    /**
     * Удаляет таблицу из базы данных по sql запросу
     *
     * @param s данные таблицы для удаления
     * @return сообщение об удалении таблицы
     * @throws IOException
     */
    private QueryResult dropTable(DropTableStatement s) throws IOException {
        db.dropTable(s.table());
        return QueryResult.message("Таблица " + s.table() + " удалена");
    }

    // INSERT

    /**
     * Собирает строку таблицы из значений INSERT и проверяет их колонками
     *
     * @param schema      схема таблицы
     * @param columnNames колонки из запроса или null
     * @param values      значения из одних скобок VALUES
     * @return проверенная строка
     * @throws DbException если число значений не совпадает или значение не подходит
     *                     колонке
     */
    private Row buildRow(TableSchema schema, List<String> columnNames, List<Object> values) {
        List<Column> columns = schema.getColumns();
        Object[] result = new Object[columns.size()]; // по умолчанию все null
        if (columnNames == null) {
            if (!(values.size() == columns.size())) {
                throw new DbException("Ожидалось значений: " + columns.size() + ", получено: " + values.size());
            }
            for (int i = 0; i < values.size(); ++i) {
                result[i] = values.get(i);
            }
        } else {
            if (!(values.size() == columnNames.size())) {
                throw new DbException("Ожидалось значений: " + columnNames.size() + ", получено: " + values.size());
            }
            Set<Integer> idxs = new HashSet<>();
            for (int i = 0; i < columnNames.size(); ++i) {
                int index = schema.indexOf(columnNames.get(i));
                result[index] = values.get(i);
                if (!idxs.add(index)) {
                    throw new DbException("Колонка под номером " + index + " указана дважды");
                }
            }
        }
        for (int i = 0; i < columns.size(); ++i) {
            result[i] = columns.get(i).check(result[i]);
        }
        return new Row(result);
    }

    /**
     * Выполняет Insert
     *
     * @param s данные запроса INSERT
     * @return сообщение о количестве добавленых строк
     */
    private QueryResult insert(InsertStatement s) throws IOException {
        Table table = db.table(s.table());
        TableSchema schema = table.schema();
        int pk = schema.getPrimaryKeyIndex();
        List<Row> rows = new ArrayList<>();
        Set<Object> newKeys = new HashSet<>();
        for (List<Object> values : s.rows()) {
            Row row = buildRow(schema, s.columns(), values);
            Object key = row.get(pk);
            if (!newKeys.add(key) || findByKey(table, key) != null) {
                throw new DbException("Нет уникальности первичного ключа " + key);
            }
            checkForeignKeys(schema, row);
            rows.add(row);
        }
        for (List<Object> values : s.rows()) {
            Row row = buildRow(schema, s.columns(), values);
            table.insert(row);
        }
        return QueryResult.message("Вставлено " + rows.size() + " строк");
    }

    // SELECT

    /**
     * Ищет строки в таблице по sql запросу
     *
     * @param s данные для запроса
     * @return сообщение о найденых строках
     */
    private QueryResult select(SelectStatement s) throws IOException {
        Table table = db.table(s.table());
        TableSchema schema = table.schema();

        List<String> names = new ArrayList<>();
        List<Integer> indexes = new ArrayList<>();
        if (s.columns() == null) {
            for (int i = 0; i < schema.getColumns().size(); i++) {
                names.add(schema.getColumns().get(i).name());
                indexes.add(i);
            }
        } else {
            for (String name : s.columns()) {
                indexes.add(schema.indexOf(name));
                names.add(schema.getColumn(name).name());
            }
        }

        Predicate<Row> filter = matcher(schema, s.where());
        List<Row> result = new ArrayList<>();
        for (StoredRow stored : find(table, s.where())) {
            Object[] values = new Object[indexes.size()];
            for (int i = 0; i < indexes.size(); i++) {
                values[i] = stored.row().get(indexes.get(i));
            }
            result.add(new Row(values));
        }
        return new QueryResult(names, result, "Найдено строк: " + result.size()
                + (usesIndex(table, s.where()) ? " (по индексу)" : " (полный перебор)"));
    }

    // UPDATE

    /**
     * Обновляет данные в таблице по sql запросу
     *
     * @param s данные для обновления
     * @return сообщение об обновлении
     * @throws IOException
     */
    private QueryResult update(UpdateStatement s) throws IOException {
        Table table = db.table(s.table());
        TableSchema schema = table.schema();
        int pk = schema.getPrimaryKeyIndex();
        for (String name : s.assignments().keySet()) {
            if (schema.indexOf(name) == pk) {
                throw new DbException("Изменять первичный ключ нельзя");
            }
        }
        List<StoredRow> matched = find(table, s.where());
        List<Row> updated = new ArrayList<>();
        for (StoredRow stored : matched) {
            Row row = stored.row();
            for (Map.Entry<String, Object> e : s.assignments().entrySet()) {
                int index = schema.indexOf(e.getKey());
                Column column = schema.getColumns().get(index);
                row = row.replace(index, column.check(e.getValue()));
            }
            checkForeignKeys(schema, row);
            updated.add(row);
        }
        for (int i = 0; i < matched.size(); i++) {
            table.update(matched.get(i).slot(), updated.get(i));
        }
        return QueryResult.message("Изменено строк: " + matched.size());
    }

    // DELETE

    /**
     * Удаляет строки из таблицы по sql запросу
     *
     * @param s данные для удаления
     * @return сообщение об удалении
     * @throws IOException
     */
    private QueryResult delete(DeleteStatement s) throws IOException {
        Table table = db.table(s.table());
        TableSchema schema = table.schema();
        List<StoredRow> matched = find(table, s.where());
        for (StoredRow stored : matched) {
            Row row = stored.row();
            checkNotReferenced(schema, row.get(schema.getPrimaryKeyIndex()));
        }
        for (StoredRow stored : matched) {
            table.delete(stored.slot());
        }
        return QueryResult.message("Удалено строк: " + matched.size());
    }

    // WHERE
    /**
     * Превращает условие WHERE в проверку строки, сравнение с NULL не выполняется
     *
     * @param schema схема таблицы
     * @param where  условие или null
     * @return проверка строки
     * @throws DbException если колонки нет или значение не подходит к ее типу
     */
    private Predicate<Row> matcher(TableSchema schema, Condition where) {
        if (where == null) {
            return row -> true;
        }
        int index = schema.indexOf(where.column());
        Column column = schema.getColumns().get(index);
        Object value = column.type().coerce(where.value());
        return row -> {
            Object actual = row.get(index);
            if (actual == null || value == null) {
                return false;
            }
            return where.operator().matches(compare(actual, value));
        };
    }

    /**
     * Сравнивает два значения одного типа.
     *
     * @param a первое значение
     * @param b второе значение
     * @return отрицательное, ноль или положительное число
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static int compare(Object a, Object b) {
        return ((Comparable) a).compareTo(b);
    }

    /**
     * Находит все строки таблицы, подходящие под условие
     *
     * @param table  таблица
     * @param filter условие
     * @return найденные строки с номерами ячеек
     */
    private static List<StoredRow> findAll(Table table, Predicate<Row> filter) {
        List<StoredRow> result = new ArrayList<>();
        for (StoredRow stored : table) {
            if (filter.test(stored.row())) {
                result.add(stored);
            }
        }
        return result;
    }

    /**
     * Ищет строку по первичному ключу
     *
     * @param table таблица
     * @param key   значение первичного ключа
     * @return найденная строка или null
     */
    private static StoredRow findByKey(Table table, Object key) throws IOException {
        return table.findByKey(key);
    }

    /**
     * Проверяет, что все внешние ключи строки ссылаются на существующие строки
     *
     * @param schema схема таблицы, в которую пишется строка
     * @param row    строка
     * @throws DbException если ссылка ведет на несуществующую строку
     */
    private void checkForeignKeys(TableSchema schema, Row row) throws IOException {
        List<Column> columns = schema.getColumns();
        for (int i = 0; i < columns.size(); i++) {
            Column c = columns.get(i);
            Object value = row.get(i);
            if (c.isForeignKey() && value != null && findByKey(db.table(c.refTable()), value) == null) {
                throw new DbException("В таблице " + c.refTable() + " нет строки с " + c.refColumn() + " = " + value);
            }
        }
    }

    /**
     * Проверяет, что на строку с данным ключом не ссылаются строки других таблиц
     *
     * @param schema схема таблицы, из которой удаляется строка
     * @param key    первичный ключ удаляемой строки
     * @throws DbException если на строку есть ссылки
     */
    private void checkNotReferenced(TableSchema schema, Object key) throws IOException{
        for (TableSchema other : db.catalog().all()) {
            List<Column> columns = other.getColumns();
            for (int i = 0; i < columns.size(); ++i) {
                Column c = columns.get(i);
                if (c.isForeignKey() && c.refTable().equalsIgnoreCase(schema.getName())) {
                    int index = i;
                    Table refTable = db.table(other.getName());
                    boolean referenced = refTable.hasIndex(index) ? !refTable.lookup(index, key).isEmpty() : !findAll(refTable, r -> Objects.equals(r.get(index), key)).isEmpty();
                    if (referenced) {
                        throw new DbException("Строку с ключом " + key + " нельзя удалить: на неё ссылается таблица " + other.getName());
                    }
                }
            }
        }
    }



    /**
     * Можно ли выполнить условие через индекс
     *
     * @param table таблица
     * @param where условие или null
     * @return true, если будет использован индекс
     */
    private static boolean usesIndex(Table table, Condition where) {
        return where != null && where.operator() == Operator.EQ
                && table.hasIndex(table.schema().indexOf(where.column()));
    }

    /**
     * Находит строки по условию через индекс если он есть, иначе полным перебором
     *
     * @param table таблица
     * @param where условие или null
     * @return найденные строки с номерами ячеек
     * @throws IOException при ошибке чтения
     */
    private List<StoredRow> find(Table table, Condition where) throws IOException {
        TableSchema schema = table.schema();
        if (usesIndex(table, where)) {
            int index = schema.indexOf(where.column());
            Object value = schema.getColumns().get(index).type().coerce(where.value());
            return table.lookup(index, value);
        }
        return findAll(table, matcher(schema, where));
    }
}
