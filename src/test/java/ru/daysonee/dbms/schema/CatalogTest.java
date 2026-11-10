package ru.daysonee.dbms.schema;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.daysonee.dbms.DbException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты каталога
 */
class CatalogTest {

    @TempDir
    Path dir;

    private static TableSchema heroes() {
        return new TableSchema("heroes", List.of(
                new Column("id", DataType.INT, 0, true, true, null, null),
                new Column("name", DataType.STRING, 30, false, true, null, null)));
    }

    private static TableSchema skins() {
        return new TableSchema("skins", List.of(
                new Column("id", DataType.INT, 0, true, true, null, null),
                new Column("hero_id", DataType.INT, 0, false, false, "heroes", "id"),
                new Column("price", DataType.DOUBLE, 0, false, false, null, null)));
    }

    @Test
    void tablesSurviveReopen() throws IOException {
        Catalog cat = Catalog.open(dir);
        cat.create(heroes());
        cat.create(skins());

        Catalog again = Catalog.open(dir);
        assertEquals(2, again.all().size());
        assertEquals(30, again.get("HEROES").getColumn("name").length());
        Column fk = again.get("skins").getColumn("hero_id");
        assertEquals("heroes", fk.refTable());
        assertEquals("id", fk.refColumn());
    }

    @Test
    void duplicateTableIsRejected() throws IOException {
        Catalog cat = Catalog.open(dir);
        cat.create(heroes());
        assertThrows(DbException.class, () -> cat.create(heroes()));
    }

    @Test
    void wrongForeignKeysAreRejected() throws IOException {
        Catalog cat = Catalog.open(dir);
        assertThrows(DbException.class, () -> cat.create(skins()));
        assertFalse(cat.exists("skins"));

        cat.create(heroes());
        TableSchema toNotKey = new TableSchema("a", List.of(
                new Column("id", DataType.INT, 0, true, true, null, null),
                new Column("hero_name", DataType.STRING, 30, false, false, "heroes", "name")));
        TableSchema wrongType = new TableSchema("b", List.of(
                new Column("id", DataType.INT, 0, true, true, null, null),
                new Column("hero_id", DataType.STRING, 10, false, false, "heroes", "id")));
        assertThrows(DbException.class, () -> cat.create(toNotKey));
        assertThrows(DbException.class, () -> cat.create(wrongType));
    }

    @Test
    void dropChecksReferencesAndIsSaved() throws IOException {
        Catalog cat = Catalog.open(dir);
        cat.create(heroes());
        cat.create(skins());

        assertThrows(DbException.class, () -> cat.drop("heroes"));
        cat.drop("skins");
        cat.drop("heroes");
        assertThrows(DbException.class, () -> cat.drop("heroes"));

        assertTrue(Catalog.open(dir).all().isEmpty());
    }

    @Test
    void openRemovesLeftoverTempFile() throws IOException {
        Files.writeString(dir.resolve("catalog.tmp"), "недописанный файл после сбоя");
        Catalog.open(dir);
        assertFalse(Files.exists(dir.resolve("catalog.tmp")));
    }
}
