package ru.daysonee.dbms.sql.statements;
/**
 * Запрос CREATE INDEX
 *
 * @param name имя индекса
 * @param table имя таблицы
 * @param column имя колонки
 */
public record CreateIndexStatement(String name, String table, String column) implements Statement {
}