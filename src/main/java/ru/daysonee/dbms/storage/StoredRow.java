package ru.daysonee.dbms.storage;

/**
 * Строка таблицы + номер ячейки, в которой она лежит
 *
 * @param slot номер ячейки в файле таблицы
 * @param row  строка
 */
public record StoredRow(int slot, Row row) {
}