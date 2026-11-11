package ru.daysonee.dbms.sql.statements;

import java.util.List;

import ru.daysonee.dbms.sql.Condition;
/**
 * Запрос SELECT
 *
 * @param table имя таблицы
 * @param columns выбранные колонки или null для *
 * @param where условие или null
 */
public record SelectStatement(String table, List<String> columns, Condition where) implements Statement {
}