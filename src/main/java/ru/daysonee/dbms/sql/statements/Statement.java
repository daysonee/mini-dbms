package ru.daysonee.dbms.sql.statements;

/** Разобранный sql запрос*/
public sealed interface Statement
        permits CreateTableStatement, DropTableStatement, CreateIndexStatement,
                InsertStatement, SelectStatement, UpdateStatement, DeleteStatement {
}