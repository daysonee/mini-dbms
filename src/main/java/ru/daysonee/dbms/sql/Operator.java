package ru.daysonee.dbms.sql;

import ru.daysonee.dbms.DbException;

/** Операторы сравнения в условии WHERE */
public enum Operator {
    /** Равно = */
    EQ,
    /** Не равно != или <> */
    NE,
    /** Меньше < */
    LT,
    /** Меньше или равно <=*/
    LE,
    /** Больше > */
    GT,
    /** Больше или равно >= */
    GE;

    /**
     * Находит оператор по знаку из запроса
     *
     * @param symbol знак
     * @return оператор
     * @throws DbException если знак не оператор сравнения
     */
    public static Operator fromSymbol(String symbol) {
        return switch (symbol) {
            case "=" -> EQ;
            case "!=", "<>" -> NE;
            case "<" -> LT;
            case "<=" -> LE;
            case ">" -> GT;
            case ">=" -> GE;
            default -> throw new DbException("Неизвестный оператор сравнения: " + symbol);
        };
    }

    /**
     * Проверяет условие по результату сравнения двух значений
     *
     * @param cmp результат сравнения меньше нуля, ноль или больше нуля
     * @return выполняется ли условие
     */
    public boolean matches(int cmp) {
        return switch (this) {
            case EQ -> cmp == 0;
            case NE -> cmp != 0;
            case LT -> cmp < 0;
            case LE -> cmp <= 0;
            case GT -> cmp > 0;
            case GE -> cmp >= 0;
        };
    }
}