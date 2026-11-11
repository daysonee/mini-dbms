package ru.daysonee.dbms.engine;

import ru.daysonee.dbms.storage.Row;

import java.util.List;

/**
 * Результат выполнения запроса
 * Для SELECT заполнены колонки и строки, для остальных запросов только сообщение
 * @param columns имена колонок результата (пустой список, если это не SELECT)
 * @param rows    строки результата (пустой список, если это не SELECT)
 * @param message сообщение для пользователя
 */
public record QueryResult(List<String> columns, List<Row> rows, String message) {

    /**
     * Создает результат запроса, который не возвращает строк
     *
     * @param message сообщение для пользователя
     * @return результат
     */
    public static QueryResult message(String message) {
        return new QueryResult(List.of(), List.of(), message);
    }
}