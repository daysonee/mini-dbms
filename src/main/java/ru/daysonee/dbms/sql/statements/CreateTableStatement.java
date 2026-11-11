package ru.daysonee.dbms.sql.statements;

import ru.daysonee.dbms.schema.Column;
import java.util.List;

/**
 * CREATE TABLE
 *
 * @param table имя таблицы
 * @param columns описания колонок
 */
public record CreateTableStatement(String table, List<Column> columns) implements Statement {
}