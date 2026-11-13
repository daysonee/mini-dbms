package ru.daysonee.dbms.ui;

import ru.daysonee.dbms.engine.QueryResult;
import ru.daysonee.dbms.storage.Row;

import javax.swing.table.AbstractTableModel;
import java.util.List;

/**
 * Модель таблицы в Swing для результата запроса
 * Колонки берутся из {@link QueryResult}
 */
public class ResultTableModel extends AbstractTableModel {
    private List<String> columns = List.of();
    private List<Row> rows = List.of();

    /**
     * Показывает результат запроса
     * @param result результат SELECT
     */
    public void setResult(QueryResult result) {
        this.columns = result.columns();
        this.rows = result.rows();
        fireTableStructureChanged();
    }

    /** Очищает таблицу */
    public void clear() {
        columns = List.of();
        rows = List.of();
        fireTableStructureChanged();
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return columns.size();
    }

    @Override
    public String getColumnName(int column) {
        return columns.get(column);
    }

    @Override
    public Object getValueAt(int row, int column) {
        Object value = rows.get(row).get(column);
        return value == null ? "NULL" : value;
    }
}