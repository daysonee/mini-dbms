package ru.daysonee.dbms.storage;

import java.util.Arrays;

/**
 * Строка таблицы в которой значения колонок в порядке схемы таблицы
 */
public final class Row {
    private final Object[] values;

    /**
     * Создает строку
     * @param values значения колонок из схемы
     */
    public Row(Object... values){
        this.values = values.clone();
    }

    /**
     * Возвращает значение колонки
     * @param index номер колонки
     * @return значение колонки или null
     */
    public Object get(int index){
        return values[index];
    }

    /**
     * Возвращает число значений в строке.
     *
     * @return число колонок
     */
    public int size() {
        return values.length;
    }

    /**
     * Возвращает новую строку, в которой одно значение поменяли, при этом исходная строка остается прежней
     *
     * @param index номер колонки
     * @param value новое значение
     * @return новая строка
     */
    public Row replace(int index, Object value) {
        Object[] copy = values.clone();
        copy[index] = value;
        return new Row(copy);
    }

    @Override
    public String toString() {
        return Arrays.toString(values);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Row r && Arrays.equals(values, r.values);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(values);
    }
}
