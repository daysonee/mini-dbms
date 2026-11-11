package ru.daysonee.dbms.sql;

/**
 * Условие WHERE: колонка, оператор и значение
 *
 * @param column   имя колонки
 * @param operator оператор сравнения
 * @param value    значение из запроса (Long, Double, String, Boolean или null)
 */
public record Condition(String column, Operator operator, Object value) {
}