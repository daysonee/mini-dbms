package ru.daysonee.dbms.schema;

import ru.daysonee.dbms.DbException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
/**
 * Типы данных в СУБД
 * У каждого типа известен размер значения в файле
 */
public enum DataType {
    /** Целое число, 4 байта */
    INT(4),
    /** Число с плавающей точкой, 8 байт */
    DOUBLE(8),
    /** Строка фиксированной длины, 2 байта на символ */
    STRING(0),
    /** Дата без времени хранится числом дней с 01-01-1970, 8 байт */
    DATE(8),
    /** Логическое значение, 1 байт */
    BOOLEAN(1);

    private final int fixedSize;

    DataType(int fixedSize) {
        this.fixedSize = fixedSize;
    }

    /**
     * Размер значения в байтах
     *
     * @param length длина строки, используются только в STRING
     * @return число байт в файле
     */
    public int sizeInBytes(int length) {
        return this == STRING ? length * 2 : fixedSize;
    }

    /**
     * Приводит значения из запросов к необходимомуму классу в java
     *
     * @param value : Long, Double, Boolean, String, null
     * @return Integer, Double, String, LocalDate, Boolean, null
     * @throws DbException если значение не подходит типу
     */
    public Object coerce(Object value){
        if (value == null){
            return null;
        }

        return switch(this) {
            case INT -> {
                if (value instanceof Long l && l >= Integer.MIN_VALUE && l<= Integer.MAX_VALUE){
                    yield l.intValue();
                }
                if (value instanceof Integer) {
                    yield value;
                }
                throw new DbException("Ожидалось целочисленное значение, пришло: " + value);
            }
            case DOUBLE -> {
                if (value instanceof Number n){
                    yield n.doubleValue();
                }
                throw new DbException("Ожидалось дробное число, пришло: " + value);
            }
            case STRING -> {
                if (value instanceof String) {
                    yield value;
                }
                throw new DbException("Ожидалась строка, пришло: " + value);
            }
            case DATE -> {
                if (value instanceof LocalDate){
                    yield value;
                }
                if (value instanceof String s){
                    try {
                        yield LocalDate.parse(s);
                    }
                    catch (DateTimeParseException e) {
                        throw new DbException("Дата ожидается в формате YYYY-MM-DD, пришло: " + s);
                    }
                }
                throw new DbException("Ожидалась дата, пришло: " + value);

            }
            case BOOLEAN -> {
                if (value instanceof Boolean) {
                    yield value;
                }
                throw new DbException("Ожидался булевый тип данных, пришло:" + value);
            }
        };
    }

    /**
     * Находит тип по имени из SQL
     * @param name имя типа из запроса
     * @return найденный тип
     * @throws DbException если типа не существует
     */
    public static DataType fromSql(String name) {
        try {
            return DataType.valueOf(name.toUpperCase());
        } catch (Exception e) {
            throw new DbException("Неизвестный тип данных: " + name);
        }

    }
}
