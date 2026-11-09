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
 */

public record Column(String name, DataType type, int length, boolean primaryKey, boolean notNull, String refTable,
        String refColumn) {
    public static final int MAX_STRING_LENGTH = 255;

    public Column {
        if (name == null || name.equals("")) {
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
     * @return возвращает размер значнения колонки в байтах в таблице
     */
    public int sizeInBytes() {
        return type.sizeInBytes(length);
    }

    /**
     * @return есть ли у колонки внешний ключ
     */
    public boolean isForeignKey() {
        return refTable != null;
    }

    /**
     * Приводит значение к типу колонки
     *
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
