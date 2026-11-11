package ru.daysonee.dbms.sql.statements;

import java.util.List;
/**
 * Запрос INSERT
 *
 * @param table имя таблицы
 * @param columns перечисленные колонки или null, если колонки не указаны
 * @param rows строки значений, каждая строка это значения из одних скобок VALUES
 */
public record InsertStatement(String table, List<String> columns, List<List<Object>> rows) implements Statement {
}