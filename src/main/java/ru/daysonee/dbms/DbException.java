package ru.daysonee.dbms;
/**
 * Ошибки работы СУБД такие как неверный запрос, несуществующая таблица или колонка,
 * нарушение ограничений
 */
public class DbException extends RuntimeException {
    /**
     * Создает исключение с сообщением для пользователя
     *
     * @param message текст ошибки
     */
    public DbException(String message) {
        super(message);
    }
}
