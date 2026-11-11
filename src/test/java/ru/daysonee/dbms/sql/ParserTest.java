package ru.daysonee.dbms.sql;

import org.junit.jupiter.api.Test;
import ru.daysonee.dbms.DbException;
import ru.daysonee.dbms.schema.DataType;
import ru.daysonee.dbms.sql.statements.*;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Тесты парсера. */
class ParserTest {

    @Test
    void parsesQueriesFromAssignment() {
        InsertStatement insert = (InsertStatement) Parser.parse("INSERT INTO cars (id, brand) VALUES (2, 'BMW'), (3, NULL);");
        assertEquals("cars", insert.table());
        assertEquals(List.of("id", "brand"), insert.columns());
        assertEquals(List.of(List.of(2L, "BMW"), Arrays.asList(3L, null)), insert.rows());

        SelectStatement select = (SelectStatement) Parser.parse("SELECT brand, price FROM cars WHERE price > 20000;");
        assertEquals(List.of("brand", "price"), select.columns());
        assertEquals(new Condition("price", Operator.GT, 20000L), select.where());
        assertNull(((SelectStatement) Parser.parse("select * from cars")).columns());

        UpdateStatement update = (UpdateStatement) Parser.parse("UPDATE cars SET price = 30000.5, sold = TRUE WHERE id = 2;");
        assertEquals(30000.5, update.assignments().get("price"));
        assertEquals(true, update.assignments().get("sold"));

        CreateTableStatement create = (CreateTableStatement) Parser.parse(
                "CREATE TABLE skins (id INT PRIMARY KEY, name STRING(40) NOT NULL, hero_id INT REFERENCES heroes(id));");
        assertTrue(create.columns().get(0).primaryKey());
        assertEquals(DataType.STRING, create.columns().get(1).type());
        assertEquals(40, create.columns().get(1).length());
        assertEquals("heroes", create.columns().get(2).refTable());

        assertInstanceOf(DeleteStatement.class, Parser.parse("DELETE FROM cars WHERE brand = 'Toyota';"));
        assertInstanceOf(DropTableStatement.class, Parser.parse("DROP TABLE cars;"));
        assertInstanceOf(CreateIndexStatement.class, Parser.parse("CREATE INDEX idx_cars_brand ON cars (brand);"));
    }

    @Test
    void wrongQueriesGiveDbException() {
        assertThrows(DbException.class, () -> Parser.parse("SELECT brand cars"));                     // нет FROM
        assertThrows(DbException.class, () -> Parser.parse("INSERT INTO cars VALUES (1, 'a'"));       // нет ')'
        assertThrows(DbException.class, () -> Parser.parse("UPDATE cars SET price 5"));               // нет '='
        assertThrows(DbException.class, () -> Parser.parse("SELECT * FROM cars WHERE id = 1 junk"));  // лишний текст
        assertThrows(DbException.class, () -> Parser.parse("CREATE VIEW v"));                         // неизвестная команда
        assertThrows(DbException.class, () -> Parser.parse(""));                                      // пустой запрос
    }
}
