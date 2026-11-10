package ru.daysonee.dbms.sql;

/** Типы токенов SQL-запроса. */
public enum TokenType {
    /** Ключевое слово или имя(SELECT, cars) */
    WORD,
    /** Целочисленное значение(67, -52)*/
    INTEGER,
    /** Дробное значение(67.67) */
    DECIMAL,
    /** Строка в 'одинарных кавычках' и без них (Pudge)*/
    STRING,
    /** Знаки () , . * = != < > <= >= */
    SYMBOL,
    /** Конец запроса */
    EOF
}
