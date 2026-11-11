package ru.daysonee.dbms.sql;

import ru.daysonee.dbms.DbException;
import ru.daysonee.dbms.schema.Column;
import ru.daysonee.dbms.schema.DataType;
import ru.daysonee.dbms.sql.statements.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Парсер SQL-подобного языка запросов.
 * Работает методом рекурсивного спуска, для каждого правила грамматики используется свой метод,
 * который читает токены по порядку и собирает объект запроса ({@link Statement}).
 */
public class Parser {
    private final List<Token> tokens;
    private int pos = 0;

    /**
     * Создаёт парсер для списка токенов. Снаружи используется {@link #parse(String)}.
     *
     * @param tokens токены запроса, последний — EOF
     */
    private Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    /**
     * Разбирает один sql запрос.
     *
     * @param sql текст запроса
     * @return разобранный запрос
     * @throws DbException если запрос написан с ошибкой
     */
    public static Statement parse(String sql) {
        return new Parser(Lexer.tokenize(sql)).statement();
    }

    /**
     * Возвращает текущий токен не сдвигаясь
     *
     * @return текущий токен
     */
    private Token peek() {
        return tokens.get(pos);
    }

    /**
     * Возвращает текущий токен и сдвигается к следующему
     *
     * @return текущий токен
     */
    private Token next() {
        Token t = tokens.get(pos);
        if (t.type() != TokenType.EOF) {
            pos++;
        }
        return t;
    }

    /**
     * Читает обязательное слово
     *
     * @param word ожидаемое слово
     * @throws DbException если следующий токен не это слово
     */
    private void expectWord(String word) {
        Token t = next();
        if (!t.isWord(word)) {
            throw error("Ожидалось " + word + ", а встретилось " + describe(t));
        }
    }

    /**
     * Читает необязательное слово, если следующий токен это слово, то сдвигается.
     *
     * @param word ожидаемое слово
     * @return true, если слово было и его прочитали
     */
    private boolean acceptWord(String word) {
        if (peek().isWord(word)) {
            pos++;
            return true;
        }
        return false;
    }

    /**
     * Читает обязательный знак
     *
     * @param symbol ожидаемый знак
     * @throws DbException если следующий токен не этот знак
     */
    private void expectSymbol(String symbol) {
        Token t = next();
        if (!t.isSymbol(symbol)) {
            throw error("Ожидалось " + symbol + ", а встретилось " + describe(t));

        }
    }

    /**
     * Читает необязательный знак, если следующий токен этот знак, то сдвигается
     *
     * @param symbol ожидаемый знак
     * @return true, если знак был и его прочитали
     */
    private boolean acceptSymbol(String symbol) {
        if (peek().isSymbol(symbol)) {
            pos++;
            return true;
        }
        return false;
    }

    /**
     * Описывает токен для сообщения об ошибке
     *
     * @param t токен
     * @return текст и позиция токена или конец запроса для EOF
     */
    private static String describe(Token t) {
        return t.type() == TokenType.EOF ? "конец запроса" : "'" + t.text() + "' в позиции " + t.position();
    }

    /**
     * Создает исключение о синтаксической ошибке. Бросает его вызывающий код: {@code throw error(...)}.
     *
     * @param message описание ошибки
     * @return исключение с префиксом "Ошибка синтаксиса"
     */
    private static DbException error(String message) {
        return new DbException("Ошибка синтаксиса. " + message);
    }

    /**
     * Читает имя таблицы, колонки или индекса
     *
     * @return имя
     * @throws DbException если следующий токен не слово
     */
    private String name() {
        Token t = next();
        if (t.type() != TokenType.WORD) {
            throw error("Ожидалось имя, а встретилось " + describe(t));
        }
        return t.text();
    }

    /**
     * Разбирает запрос целиком, определяет команду по первому слову,
     * разрешает «;» в конце и проверяет, что после запроса ничего нет
     *
     * @return разобранный запрос
     * @throws DbException если команда неизвестна или после запроса лишний текст
     */
    private Statement statement() {
        Token first = peek();
        Statement result;
        if (first.isWord("SELECT")) {
            result = parseSelect();
        } else if (first.isWord("INSERT")) {
            result = parseInsert();
        } else if (first.isWord("UPDATE")) {
            result = parseUpdate();
        } else if (first.isWord("DELETE")) {
            result = parseDelete();
        } else if (first.isWord("DROP")) {
            result = parseDropTable();
        } else if (first.isWord("CREATE")) {
            if (tokens.get(pos + 1).isWord("INDEX")) {
                result = parseCreateIndex();
            } else if (tokens.get(pos + 1).isWord("TABLE")) {
                result = parseCreateTable();
            } else {
                throw error("После CREATE ожидалось TABLE или INDEX, а встретилось " + describe(tokens.get(pos + 1)));
            }
        } else {
            throw error("Неизвестная команда: " + describe(first));
        }
        acceptSymbol(";");
        if (peek().type() != TokenType.EOF) {
            throw error("Лишний текст после конца запроса: " + describe(peek()));
        }
        return result;
    }

    /**
     * select = "SELECT" ( "*" | name { "," name } ) "FROM" name [ where ]
     *
     * @return запрос SELECT
     */
    private SelectStatement parseSelect() {
        expectWord("SELECT");
        List<String> columns = null;
        if (!acceptSymbol("*")) {
            columns = new ArrayList<>();
            columns.add(name());
            while (acceptSymbol(",")) {
                columns.add(name());
            }
        }
        expectWord("FROM");
        String table = name();
        return new SelectStatement(table, columns, optionalWhere());
    }

    /**
     * insert = "INSERT" "INTO" name [ "(" name { "," name } ")" ] "VALUES" tuple { "," tuple }
     *
     * @return запрос INSERT
     */
    private InsertStatement parseInsert() {
        expectWord("INSERT");
        expectWord("INTO");
        String table = name();
        List<String> columns = null;
        if (acceptSymbol("(")) {
            columns = new ArrayList<>();
            columns.add(name());
            while (acceptSymbol(",")) {
                columns.add(name());
            }
            expectSymbol(")");
        }
        expectWord("VALUES");
        List<List<Object>> rows = new ArrayList<>();
        rows.add(tuple());
        while (acceptSymbol(",")) {
            rows.add(tuple());
        }
        return new InsertStatement(table, columns, rows);
    }

    /**
     * tuple = "(" literal { "," literal } ")" - значения одной строки в INSERT.
     *
     * @return значения строки, может содержать null
     */
    private List<Object> tuple() {
        expectSymbol("(");
        ArrayList<Object> t = new ArrayList<>();
        t.add(literal());
        while (acceptSymbol(",")) {
            t.add(literal());
        }
        expectSymbol(")");
        return t;
    }

    /**
     * update = "UPDATE" name "SET" name "=" literal { "," name "=" literal } [ where ]
     *
     * @return запрос UPDATE
     */
    private UpdateStatement parseUpdate() {
        expectWord("UPDATE");
        String table = name();
        expectWord("SET");
        Map<String, Object> sets = new LinkedHashMap<>();
        do {
            String col = name();
            expectSymbol("=");
            sets.put(col, literal());
        } while (acceptSymbol(","));
        return new UpdateStatement(table, sets, optionalWhere());
    }

    /**
     * delete = "DELETE" "FROM" name [ where ]
     *
     * @return запрос DELETE
     */
    private DeleteStatement parseDelete() {
        expectWord("DELETE");
        expectWord("FROM");
        String table = name();
        Condition where = optionalWhere();
        return new DeleteStatement(table, where);
    }

    /**
     * drop_table = "DROP" "TABLE" name
     *
     * @return запрос DROP TABLE
     */
    private DropTableStatement parseDropTable() {
        expectWord("DROP");
        expectWord("TABLE");
        String table = name();
        return new DropTableStatement(table);
    }

    /**
     * create_index = "CREATE" "INDEX" name "ON" name "(" name ")"
     *
     * @return запрос CREATE INDEX
     */
    private CreateIndexStatement parseCreateIndex() {
        expectWord("CREATE");
        expectWord("INDEX");
        String name = name();
        expectWord("ON");
        String table = name();
        expectSymbol("(");
        String column = name();
        expectSymbol(")");
        return new CreateIndexStatement(name, table, column);
    }

    /**
     * create_table = "CREATE" "TABLE" name "(" column_def { "," column_def } ")"
     *
     * @return запрос CREATE TABLE
     */
    private CreateTableStatement parseCreateTable() {
        expectWord("CREATE");
        expectWord("TABLE");
        String table = name();
        expectSymbol("(");
        List<Column> columns = new ArrayList<>();
        columns.add(columnDef());
        while (acceptSymbol(",")) {
            columns.add(columnDef());
        }
        expectSymbol(")");
        return new CreateTableStatement(table, columns);
    }

    /**
     * column_def = name type [ "(" INTEGER ")" ] { "PRIMARY" "KEY" | "NOT" "NULL" | "REFERENCES" name "(" name ")" }
     * Модификаторы могут идти в любом порядке. Правильность описания проверяет конструктор {@link Column}.
     *
     * @return описание колонки
     * @throws DbException если тип неизвестен или описание некорректно
     */
    private Column columnDef() {
        String name = name();
        Token typeToken = next();
        if (typeToken.type() != TokenType.WORD) {
            throw error("Ожидался тип колонки " + name + ", а встретилось " + describe(typeToken));
        }
        DataType type = DataType.fromSql(typeToken.text());

        int length = 0;
        if (acceptSymbol("(")) {
            Token n = next();
            if (n.type() != TokenType.INTEGER) {
                throw error("Ожидалась длина строки, а встретилось " + describe(n));
            }
            length = Integer.parseInt(n.text());
            expectSymbol(")");
        }

        boolean primaryKey = false;
        boolean notNull = false;
        String refTable = null;
        String refColumn = null;
        while (true) {
            if (acceptWord("PRIMARY")) {
                expectWord("KEY");
                primaryKey = true;
            } else if (acceptWord("NOT")) {
                expectWord("NULL");
                notNull = true;
            } else if (acceptWord("REFERENCES")) {
                refTable = name();
                expectSymbol("(");
                refColumn = name();
                expectSymbol(")");
            } else {
                break;
            }
        }
        return new Column(name, type, length, primaryKey, notNull, refTable, refColumn);
    }

    /**
     * where = "WHERE" name operator literal, необязательное условие
     *
     * @return условие или null, если WHERE нет
     * @throws DbException если после WHERE нет оператора сравнения или значения
     */
    private Condition optionalWhere() {
        if (!acceptWord("WHERE")) {
            return null;
        }
        String column = name();
        Token op = next();
        if (op.type() != TokenType.SYMBOL) {
            throw error("Ожидался оператор сравнения после " + column + ", а встретилось " + describe(op));
        }
        return new Condition(column, Operator.fromSymbol(op.text()), literal());
    }

    /**
     * literal = INTEGER | DECIMAL | STRING | "TRUE" | "FALSE" | "NULL"
     *
     * @return Long, Double, String, Boolean или null
     * @throws DbException если токен не значение или число слишком большое
     */
    private Object literal() {
        Token t = next();
        return switch (t.type()) {
            case INTEGER -> {
                try {
                    yield Long.parseLong(t.text());
                } catch (NumberFormatException e) {
                    throw error("Слишком большое число " + describe(t));
                }
            }
            case DECIMAL -> Double.parseDouble(t.text());
            case STRING -> t.text();
            case WORD -> {
                if (t.isWord("TRUE")) {
                    yield true;
                }
                if (t.isWord("FALSE")) {
                    yield false;
                }
                if (t.isWord("NULL")) {
                    yield null;
                }
                throw error("Ожидалось значение, а встретилось " + describe(t));
            }
            default -> throw error("Ожидалось значение, а встретилось " + describe(t));
        };
    }
}
