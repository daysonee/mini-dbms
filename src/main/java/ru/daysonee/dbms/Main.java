package ru.daysonee.dbms;

import ru.daysonee.dbms.ui.MainWindow;

import javax.swing.SwingUtilities;
import java.nio.file.Path;

/** Точка входа. Открывает окно с базой данных */
public final class Main {

    private Main() {
    }

    /**
     * Запускает программу
     * @param args необязательный путь к папке базы данных
     */
    public static void main(String[] args) {
        Path dir = Path.of(args.length > 0 ? args[0] : "data");
        SwingUtilities.invokeLater(() -> new MainWindow(dir).setVisible(true));
    }
}