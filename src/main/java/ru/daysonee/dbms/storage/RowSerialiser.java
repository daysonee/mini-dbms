package ru.daysonee.dbms.storage;

import ru.daysonee.dbms.schema.Column;
import ru.daysonee.dbms.schema.TableSchema;

import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.util.List;

/**
 * Превращает строку таблицы в массив байт и обратно по схеме таблицы
 * Формат: битовая маска NULL, после значения колонок фиксированного размера
 */
public class RowSerialiser {
    private final List<Column> columns;
    private final int bitmapSize;
    private final int size;

    /**
     * Создает сериализатор для таблицы
     *
     * @param schema схема таблицы
     */
    public RowSerialiser(TableSchema schema) {
        this.columns = schema.getColumns();
        this.bitmapSize = (columns.size() + 7) / 8;
        int size = bitmapSize;
        for (Column c : columns) {
            size += c.sizeInBytes();
        }
        this.size = size;
    }

    /**
     * Возвращает размер строки в байтах, без байта статуса ячейки.
     *
     * @return размер в байтах
     */
    public int size() {
        return size;
    }

    /**
     * Превращает строку в массив байт
     *
     * @param row строка которую надо перевести в байты
     * @return массив длиной {@link #size()}
     */
    public byte[] toBytes(Row row) {
        ByteBuffer buf = ByteBuffer.allocate(size);

        byte[] bitmap = new byte[bitmapSize];
        for (int i = 0; i < columns.size(); ++i) {
            if (row.get(i) == null) {
                int byteIndex = i / 8;
                int bit = i % 8;
                bitmap[byteIndex] |= (byte) (1 << bit);
            }
        }
        buf.put(bitmap);

        for (int i = 0; i < columns.size(); ++i) {
            Column c = columns.get(i);
            Object v = row.get(i);
            if (v == null) {
                buf.position(buf.position() + c.sizeInBytes());
                continue;
            }
            switch (c.type()) {
                case INT -> buf.putInt((Integer) v);
                case DOUBLE -> buf.putDouble((Double) v);
                case BOOLEAN -> buf.put((byte) ((Boolean) v ? 1 : 0));
                case DATE -> buf.putLong(((LocalDate) v).toEpochDay());
                case STRING -> {
                    for (int j = 0; j < c.length(); ++j) {
                        char ch = (j < v.toString().length() ? v.toString().charAt(j) : '\0');
                        buf.putChar(ch);
                    }
                }

            }
        }
        return buf.array();
    }
    /**
     * Превращает массив байтов в строку
     * @param bytes массив байтов
     * @return строку
     */
    public Row fromBytes(byte[] bytes) {
        ByteBuffer buf = ByteBuffer.wrap(bytes);

        byte[] bitmap = new byte[bitmapSize];
        buf.get(bitmap);

        Object[] values = new Object[columns.size()];
        for (int i = 0; i < columns.size(); ++i) {
            Column c = columns.get(i);
            int byteIndex = i / 8;
            int bit = i % 8;
            boolean isNull = (bitmap[byteIndex] & (1 << bit)) != 0;
            if (isNull) {
                buf.position(buf.position() + c.sizeInBytes());
                continue;
            }
            values[i] = switch (c.type()) {
                case INT -> buf.getInt();
                case DOUBLE -> buf.getDouble();
                case BOOLEAN -> {
                    if (buf.get() != 0) {
                        yield true;
                    } else {
                        yield false;
                    }
                }
                case DATE -> LocalDate.ofEpochDay(buf.getLong());
                case STRING -> {
                    StringBuilder sb = new StringBuilder();
                    for (int j = 0; j < c.length(); ++j) {
                        char ch = buf.getChar();
                        if (ch != '\0') {
                            sb.append(ch);
                        }
                    }
                    yield sb.toString();
                }
            };
        }
        return new Row(values);

    }
}
