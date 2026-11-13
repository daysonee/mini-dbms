package ru.daysonee.dbms.sql;

import java.util.ArrayList;
import java.util.List;

/** Делит текст из нескольких запросов на отдельные запросы по точке с запятой*/
public final class SqlScript {

    private SqlScript() {
    }

    /**
     * Разбивает текст на запросы
     * @param text полный текст запросов
     * @return список запросов
     */
    public static List<String> split(String text) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inString = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\'') {
                inString = !inString;
            }
            if (c == ';' && !inString) {
                addIfNotBlank(result, current);
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        addIfNotBlank(result, current);
        return result;
    }

    private static void addIfNotBlank(List<String> result, StringBuilder sb) {
        String s = sb.toString().trim();
        if (!s.isEmpty()) {
            result.add(s);
        }
    }
}