package ru.daysonee.dbms.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.daysonee.dbms.DbException;
import ru.daysonee.dbms.schema.Column;
import ru.daysonee.dbms.schema.DataType;
import ru.daysonee.dbms.schema.TableSchema;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Тесты файла таблицы: хранение строк, NULL, список свободных ячеек, перебор. */
class TableFileTest {

    @TempDir
    Path dir;

    private static RowSerialiser skins() {
        return new RowSerialiser(new TableSchema("skins", List.of(
                new Column("id", DataType.INT, 0, true, true, null, null),
                new Column("name", DataType.STRING, 40, false, false, null, null),
                new Column("released", DataType.DATE, 0, false, false, null, null),
                new Column("price", DataType.DOUBLE, 0, false, false, null, null),
                new Column("tradable", DataType.BOOLEAN, 0, false, false, null, null))));
    }

    @Test
    void rowsWithNullsSurviveReopen() throws IOException {
        Row full = new Row(1, "Manifold Paradox", LocalDate.of(2015, 3, 1), 34.5, true);
        Row empty = new Row(2, null, null, null, null);
        Path file = dir.resolve("skins.tbl");

        try (TableFile t = TableFile.create(file, skins())) {
            t.insert(full);
            t.insert(empty);
        }
        try (TableFile t = TableFile.open(file, skins())) {
            assertEquals(2, t.getRowCount());
            assertEquals(full, t.read(0));
            assertEquals(empty, t.read(1));
        }
    }

    @Test
    void deletedSlotIsReused() throws IOException {
        try (TableFile t = TableFile.create(dir.resolve("skins.tbl"), skins())) {
            for (int i = 1; i <= 3; i++) {
                t.insert(new Row(i, "S" + i, null, 1.0, true));
            }
            t.delete(1);
            assertNull(t.read(1));
            assertThrows(DbException.class, () -> t.delete(1));

            assertEquals(1, t.insert(new Row(4, "S4", null, 1.0, true)));
            assertEquals(3, t.getSlotCount());
            assertEquals(3, t.getRowCount());
        }
    }

    @Test
    void updateAndIteratorSkipFreeSlots() throws IOException {
        try (TableFile t = TableFile.create(dir.resolve("skins.tbl"), skins())) {
            for (int i = 1; i <= 4; i++) {
                t.insert(new Row(i, "S" + i, null, 1.0, true));
            }
            t.delete(0);
            t.delete(2);
            t.update(3, new Row(4, "S4", null, 99.0, false));

            List<Integer> slots = new ArrayList<>();
            for (StoredRow r : t) {
                slots.add(r.slot());
            }
            assertEquals(List.of(1, 3), slots);                        
            assertEquals(99.0, t.read(3).get(3));
        }
    }

    @Test
    void openWithAnotherSchemaFails() throws IOException {
        Path file = dir.resolve("skins.tbl");
        TableFile.create(file, skins()).close();

        RowSerialiser other = new RowSerialiser(new TableSchema("other",
                List.of(new Column("id", DataType.INT, 0, true, true, null, null))));
        assertThrows(IOException.class, () -> TableFile.open(file, other));
    }
}
