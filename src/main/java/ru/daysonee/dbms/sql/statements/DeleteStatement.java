package ru.daysonee.dbms.sql.statements;

import ru.daysonee.dbms.sql.Condition;

/**
 * Запрос DELETE
 *
 * @param table имя таблицы
 * @param where условие или null, если WHERE нет
 */
public record DeleteStatement(String table, Condition where) implements Statement {
}