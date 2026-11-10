package ru.daysonee.dbms.sql;
/**
 * Минимальная смысловая единица запроса
 * @param type тип токена
 * @param text текст токена
 * @param position позиция токена начиная с 0
 */
public record Token(TokenType type, String text, int position) {
    /**
     * Проверяет, что токен это слово, равное данному без учета регистра
     *
     * @param word ожидаемое слово
     * @return true, если совпадает
     */
    public boolean isWord(String word) {
        return type == TokenType.WORD && text.equalsIgnoreCase(word);
    }

    /**
     * Проверяет, что токен данный знак
     *
     * @param symbol ожидаемый знак
     * @return true, если совпадает
     */
    public boolean isSymbol(String symbol) {
        return type == TokenType.SYMBOL && text.equals(symbol);
    }

    @Override
    public String toString() {
        return type + "(" + text + ")";
    }
}


