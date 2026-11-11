package ru.daysonee.dbms.schema;

/**
 * Описание индекса из запроса CREATE INDEX.
 * @param name имя индекса
 * @param table имя таблицы
 * @param column имя колонки
 */
public record IndexInfo(String name, String table, String column) {
}