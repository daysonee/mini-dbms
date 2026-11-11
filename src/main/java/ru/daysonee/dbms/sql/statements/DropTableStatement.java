package ru.daysonee.dbms.sql.statements;

/**
 * Запрос DROP TABLE
 *
 * @param table имя таблицы
 */
public record DropTableStatement(String table) implements Statement {
}