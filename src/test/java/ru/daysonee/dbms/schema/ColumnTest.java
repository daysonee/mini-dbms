package ru.daysonee.dbms.schema;

import org.junit.jupiter.api.Test;
import ru.daysonee.dbms.DbException;

import static org.junit.jupiter.api.Assertions.*;

/** Тесты описания колонки */
class ColumnTest {

    private static Column column(String name, DataType type, int length) {
        return new Column(name, type, length, false, false, null, null);
    }

    @Test
    void invalidColumnCannotBeCreated() {
        assertThrows(DbException.class, () -> column("   ", DataType.INT, 0));
        assertThrows(DbException.class, () -> column("x", null, 0));
        assertThrows(DbException.class, () -> column("s", DataType.STRING, 0));
        assertThrows(DbException.class, () -> column("x", DataType.INT, 5));
        assertThrows(DbException.class,
                () -> new Column("hero_id", DataType.INT, 0, false, false, "heroes", null));  // неполный FK
    }

    @Test
    void nullOnlyWhereAllowed() {
        assertNull(column("price", DataType.DOUBLE, 0).check(null));
        Column notNull = new Column("name", DataType.STRING, 30, false, true, null, null);
        Column pk = new Column("id", DataType.INT, 0, true, false, null, null);
        assertThrows(DbException.class, () -> notNull.check(null));
        assertThrows(DbException.class, () -> pk.check(null));
    }

    @Test
    void checkConvertsTypeAndChecksLength() {
        assertEquals(30000.0, column("price", DataType.DOUBLE, 0).check(30000L));
        assertThrows(DbException.class, () -> column("price", DataType.DOUBLE, 0).check("abc"));

        Column name = column("name", DataType.STRING, 5);
        assertEquals("Pudge", name.check("Pudge"));                       
        assertThrows(DbException.class, () -> name.check("Juggernaut"));
    }
}
