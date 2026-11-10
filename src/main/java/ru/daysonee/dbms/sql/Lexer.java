package ru.daysonee.dbms.sql;

import ru.daysonee.dbms.DbException;

import java.util.ArrayList;
import java.util.List;

/** Разбивает текст sql запроса на токены */
public class Lexer {
    private final String input;
    private int pos = 0;
    private final List<Token> tokens = new ArrayList<>();

    private Lexer(String input) {
        this.input = input;
    }

    /**
     * Токенезирует текст запроса
     *
     * @param input текст запроса
     * @return список токенов, где последний всего EOF
     * @throws DbException если в запросе неизвестный символ
     */
    public static List<Token> tokenize(String input) {
        return new Lexer(input).run();
    }

    private List<Token> run() {
        while (pos < input.length()) {
            char c = input.charAt(pos);
            if (Character.isWhitespace(c)) {
                pos++;
            } else if (Character.isLetter(c) || c == '_') {
                readWord();
            } else if (Character.isDigit(c) || c == '-' && isDigitAt(pos + 1)) {
                readNumber();
            } else if (c == '\'') {
                readString();
            } else {
                readSymbol();
            }

        }
        tokens.add(new Token(TokenType.EOF, "", pos));
        return tokens;
    }

    /**
     * Есть ли в позиции pos цифра
     *
     * @param pos индек чара
     * @return true если есть цифра
     */
    private boolean isDigitAt(int pos) {
        return (pos < input.length() && Character.isDigit(input.charAt(pos)));
    }

    private void readWord() {
        int start = pos;
        while (pos < input.length() && (Character.isLetterOrDigit(input.charAt(pos)) || input.charAt(pos) == '_')) {
            pos++;
        }
        tokens.add(new Token(TokenType.WORD, input.substring(start, pos), start));
    }

    private void readNumber() {
        int start = pos;
        if (input.charAt(pos) == '-') {
            pos++;
        }
        while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
            pos++;
        }
        if (pos < input.length() && input.charAt(pos) == '.' && isDigitAt(pos + 1)) {
            pos++;
            while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
                pos++;
            }
            tokens.add(new Token(TokenType.DECIMAL, input.substring(start, pos), start));
        } else {
            tokens.add(new Token(TokenType.INTEGER, input.substring(start, pos), start));
        }
    }

    private void readString() {
        int start = pos;
        pos++;
        StringBuilder sb = new StringBuilder();
        while (pos < input.length()) {
            char c = input.charAt(pos);
            if (c == '\'' && pos + 1 < input.length() && input.charAt(pos + 1) == '\'') {
                sb.append(c);
                pos += 2;
            } else if (c == '\'') {
                pos++;
                tokens.add(new Token(TokenType.STRING, sb.toString(), start));
                return;
            } else {
                sb.append(c);
                pos++;
            }
        }
        throw new DbException("Незакрытая строка, начатая в позиции " + start);
    }

        private void readSymbol() {
        int start = pos;
        if (input.length() - pos >= 2){
            String two = input.substring(pos, pos+2);
            String[] arr = {">=", "<=", "!=", "<>"};
            if (List.of(">=", "<=", "!=", "<>").contains(two)){
                pos+=2;
                tokens.add(new Token(TokenType.SYMBOL, two, start));
                return;
            }
        }
        char c = input.charAt(pos);
        if("(),;*=<>".indexOf(c) >= 0){
            pos++;
            tokens.add(new Token(TokenType.SYMBOL, String.valueOf(c), start));
            return;
        }
        throw new DbException("Неожиданный символ '" + c + "' на позиции " + pos);
    }
}
