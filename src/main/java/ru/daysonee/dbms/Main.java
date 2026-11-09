package ru.daysonee.dbms;

import javax.swing.SwingUtilities;

import ru.daysonee.dbms.ui.MainWindow;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MainWindow().setVisible(true));
    }
}