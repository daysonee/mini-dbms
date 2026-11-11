package ru.daysonee.dbms.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.daysonee.dbms.DbException;
import ru.daysonee.dbms.storage.Row;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Тесты исполнителя запросов */
class QueryExecutorTest {

    @TempDir
    Path dir;

    private static final String HEROES =
            "CREATE TABLE heroes (id INT PRIMARY KEY, name STRING(30) NOT NULL, melee BOOLEAN)";
    private static final String SKINS =
            "CREATE TABLE skins (id INT PRIMARY KEY, name STRING(40), hero_id INT REFERENCES heroes(id), price DOUBLE)";

    @Test
    void insertSelectUpdateDeleteAndReopen() throws IOException {
        try (Database db = Database.open(dir)) {
            QueryExecutor ex = new QueryExecutor(db);
            ex.execute(HEROES);
            ex.execute(SKINS);
            ex.execute("INSERT INTO heroes VALUES (1, 'Pudge', TRUE), (2, 'Lina', FALSE)");
            ex.execute("INSERT INTO skins VALUES (10, 'Dragonclaw Hook', 1, 950.0)");
            ex.execute("INSERT INTO skins (id, hero_id, name) VALUES (11, 2, 'Fiery Soul'), (12, 1, 'Feast')");

            QueryResult r = ex.execute("SELECT name, price FROM skins WHERE hero_id = 1");
            assertEquals(List.of("name", "price"), r.columns());
            assertEquals(List.of(new Row("Dragonclaw Hook", 950.0), new Row("Feast", null)), r.rows());

            ex.execute("UPDATE skins SET price = 30 WHERE id = 11");          
            assertEquals(List.of(new Row(10), new Row(11)),
                    ex.execute("SELECT id FROM skins WHERE price > 20").rows());

            ex.execute("DELETE FROM skins WHERE hero_id = 1");
            ex.execute("DELETE FROM heroes WHERE id = 1");
        }
        try (Database db = Database.open(dir)) {
            QueryExecutor ex = new QueryExecutor(db);
            assertEquals(List.of(new Row(11, "Fiery Soul", 2, 30.0)), ex.execute("SELECT * FROM skins").rows());
            assertEquals(1, ex.execute("SELECT * FROM heroes").rows().size());
        }
    }

    @Test
    void constraintsAreChecked() throws IOException {
        try (Database db = Database.open(dir)) {
            QueryExecutor ex = new QueryExecutor(db);
            ex.execute(HEROES);
            ex.execute(SKINS);
            ex.execute("INSERT INTO heroes VALUES (1, 'Pudge', TRUE)");
            ex.execute("INSERT INTO skins VALUES (10, 'Hook', 1, 1.0)");

            assertThrows(DbException.class, () -> ex.execute("INSERT INTO heroes VALUES (1, 'Copy', NULL)"));   // ключ занят
            assertThrows(DbException.class, () -> ex.execute("INSERT INTO heroes (id) VALUES (3)"));             // name NOT NULL
            assertThrows(DbException.class, () -> ex.execute("INSERT INTO skins VALUES (11, 'X', 99, 1.0)"));   // героя 99 нет
            assertThrows(DbException.class, () -> ex.execute("DELETE FROM heroes WHERE id = 1"));               // на него ссылается скин
            assertThrows(DbException.class, () -> ex.execute("DROP TABLE heroes"));                             // на таблицу ссылаются
            assertThrows(DbException.class, () -> ex.execute("UPDATE skins SET id = 5 WHERE id = 10"));         // ключ менять нельзя

            assertThrows(DbException.class,
                    () -> ex.execute("INSERT INTO heroes VALUES (2, 'A', TRUE), (2, 'B', TRUE)"));
            assertEquals(1, ex.execute("SELECT * FROM heroes").rows().size());
        }
    }
}
