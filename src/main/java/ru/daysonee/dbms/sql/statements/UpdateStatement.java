package ru.daysonee.dbms.sql.statements;

import java.util.Map;

import ru.daysonee.dbms.sql.Condition;
/**
 * Запрос UPDATE
 *
 * @param table имя таблицы
 * @param assignments новые значения, имя колонки -> значение, в порядке из запроса
 * @param where условие или null
 */
public record UpdateStatement(String table, Map<String, Object> assignments, Condition where) implements Statement {
}