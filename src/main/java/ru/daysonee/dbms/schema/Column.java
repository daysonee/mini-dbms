package ru.daysonee.dbms.schema;

import ru.daysonee.dbms.DbException;

/**
 * Описание колонки в таблице
 *
 * @param name       имя колонки
 * @param type       тип данных
 * @param length     длина строки String или 0
 * @param primaryKey первичный ключ или нет
 * @param notNull    допускается ли Null
 * @param refTable   таблица на которую ссылается внешний ключ или Null
 * @param refColumn  колонка на которую ссылается внешний ключ или Null
 *
 */

public record Column(String name, DataType type, int length, boolean primaryKey, boolean notNull, String refTable,
        String refColumn) {
    public static final int MAX_STRING_LENGTH = 255;
    /**
     * Проверяет описание колонки
     * @throws DbException если описание неверное
     */
    public Column {
        if (type == null) {
            throw new DbException("Тип колонки не может быть null");
        }
        if (name == null || name.isBlank()) {
            throw new DbException("имя не должно быть пустым или равнять null");
        }
        if (type == DataType.STRING) {
            if (length < 1 || length > MAX_STRING_LENGTH) {
                throw new DbException("Длина строки выходит за пределы от 1 до " + MAX_STRING_LENGTH);
            }
        } else {
            if (length != 0) {
                throw new DbException("Ожидаемая длина 0, пришло: " + length);
            }
        }
        if (!((refColumn == null && refTable == null) || (refColumn != null & refTable != null))) {
            throw new DbException("Данные о ссылке на таблицу и на колонку в таблице не соответсвуют");
        }

    }

    /**
     * Возвращает размер колонки в байтах
     * @return возвращает размер значнения колонки в байтах в таблице
     */
    public int sizeInBytes() {
        return type.sizeInBytes(length);
    }

    /**
     * Проверяет, является ли колонка внешним ключом
     * @return true, если колонка ссылается на другую таблицу
     */
    public boolean isForeignKey() {
        return refTable != null;
    }

    /**
     * Приводит значение к типу колонки
     * @param value значение из запроса или null
     * @return значение приведенное к типу колонки или null
     * @throws DbException если значение не подходит
     */
    public Object check(Object value) {
        if (value == null) {
            if (notNull || primaryKey) {
                throw new DbException("Колонка " + name + " не может быть NULL");
            } else {
                return null;
            }
        }
        Object v = type.coerce(value);
        if (v instanceof String s) {
            s = v.toString();
            if (s.length() > length) {
                throw new DbException("Строка длиннее " + length + "символов в колонке " + name);
            }
        }
        return v;
    }
}
