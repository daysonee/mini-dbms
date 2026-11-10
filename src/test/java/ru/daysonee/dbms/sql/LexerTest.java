package ru.daysonee.dbms.sql;

import org.junit.jupiter.api.Test;
import ru.daysonee.dbms.DbException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Тесты лексера. */
class LexerTest {

    @Test
    void splitsQueryIntoTokensOfAllKinds() {
        List<Token> t = Lexer.tokenize("update cars SET name = 'O''Brien' WHERE price >= -2.5 AND id <> 7");

        assertEquals(List.of(
                "WORD(update)", "WORD(cars)", "WORD(SET)", "WORD(name)", "SYMBOL(=)", "STRING(O'Brien)",
                "WORD(WHERE)", "WORD(price)", "SYMBOL(>=)", "DECIMAL(-2.5)",
                "WORD(AND)", "WORD(id)", "SYMBOL(<>)", "INTEGER(7)", "EOF()"),
                t.stream().map(Token::toString).toList());

        assertTrue(t.get(0).isWord("UPDATE"));     
        assertEquals(23, t.get(5).position());
    }

    @Test
    void errorsGiveDbExceptionInsteadOfCrash() {
        assertThrows(DbException.class, () -> Lexer.tokenize("SELECT # FROM cars"));
        assertThrows(DbException.class, () -> Lexer.tokenize("INSERT INTO cars VALUES ('Toyota"));
        assertDoesNotThrow(() -> Lexer.tokenize("SELECT * FROM cars"));
        assertDoesNotThrow(() -> Lexer.tokenize("DELETE FROM cars WHERE brand = 'BMW'"));
    }
}
