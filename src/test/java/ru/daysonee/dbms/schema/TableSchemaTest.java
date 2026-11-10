package ru.daysonee.dbms.schema;

import org.junit.jupiter.api.Test;
import ru.daysonee.dbms.DbException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Тесты схемы таблицы */
class TableSchemaTest {

    private static Column pk(String name) {
        return new Column(name, DataType.INT, 0, true, true, null, null);
    }

    private static Column col(String name, DataType type, int length) {
        return new Column(name, type, length, false, false, null, null);
    }

    @Test
    void findsColumnsIgnoringCase() {
        TableSchema t = new TableSchema("heroes",
                List.of(pk("id"), col("name", DataType.STRING, 30), col("melee", DataType.BOOLEAN, 0)));
        assertEquals("id", t.getPrimaryKey().name());
        assertEquals(1, t.indexOf("NAME"));
        assertEquals(DataType.BOOLEAN, t.getColumn("Melee").type());
    }

    @Test
    void unknownColumnGivesDbException() {
        TableSchema t = new TableSchema("heroes", List.of(pk("id")));
        assertThrows(DbException.class, () -> t.getColumn("price"));
    }

    @Test
    void invalidSchemaCannotBeCreated() {
        assertThrows(DbException.class,
                () -> new TableSchema("t", List.of(pk("id"), col("ID", DataType.INT, 0))));  
        assertThrows(DbException.class,
                () -> new TableSchema("t", List.of(col("a", DataType.INT, 0))));
        assertThrows(DbException.class,
                () -> new TableSchema("t", List.of(pk("a"), pk("b"))));
        assertThrows(DbException.class,
                () -> new TableSchema("t", List.of(
                        new Column("id", DataType.DOUBLE, 0, true, true, null, null))));
    }
}
