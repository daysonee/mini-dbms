package ru.daysonee.dbms.schema;

import org.junit.jupiter.api.Test;
import ru.daysonee.dbms.DbException;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/** Тесты типов данных*/
class DataTypeTest {

    @Test
    void coerceConvertsValuesFromQuery() {
        assertEquals(Integer.valueOf(2), DataType.INT.coerce(2L));
        assertEquals(30000.0, DataType.DOUBLE.coerce(30000L));
        assertEquals(LocalDate.of(2013, 7, 9), DataType.DATE.coerce("2013-07-09"));
        assertNull(DataType.STRING.coerce(null));
    }

    @Test
    void coerceRejectsWrongValuesWithDbException() {
        assertThrows(DbException.class, () -> DataType.INT.coerce(5_000_000_000L));
        assertThrows(DbException.class, () -> DataType.INT.coerce("abc"));
        assertThrows(DbException.class, () -> DataType.DATE.coerce("вчера"));         
        assertThrows(DbException.class, () -> DataType.BOOLEAN.coerce("true"));
    }

    @Test
    void fromSqlIgnoresCaseAndRejectsUnknown() {
        assertEquals(DataType.STRING, DataType.fromSql("String"));
        assertThrows(DbException.class, () -> DataType.fromSql("VARCHAR"));
    }
}
