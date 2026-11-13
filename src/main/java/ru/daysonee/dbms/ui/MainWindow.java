package ru.daysonee.dbms.ui;

import ru.daysonee.dbms.DbException;
import ru.daysonee.dbms.engine.Database;
import ru.daysonee.dbms.engine.QueryExecutor;
import ru.daysonee.dbms.engine.QueryResult;
import ru.daysonee.dbms.schema.TableSchema;
import ru.daysonee.dbms.sql.SqlScript;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.nio.file.Path;
import java.util.List;

/**
 * Главное окно: редактор sql, таблица с результатом и список таблиц базы. Запросы выполняет {@link QueryExecutor}
 */
public class MainWindow extends JFrame {

    /** Действие, которое может бросить исключение */
    @FunctionalInterface
    interface DbAction {
        /**
         * Выполняет действие.
         * @throws Exception любая ошибка
         */
        void run() throws Exception;
    }

    private static final String EXAMPLE = """
            CREATE TABLE heroes (id INT PRIMARY KEY, name STRING(30) NOT NULL, released DATE, melee BOOLEAN);
            CREATE TABLE skins (id INT PRIMARY KEY, name STRING(40), hero_id INT REFERENCES heroes(id), price DOUBLE);
            INSERT INTO heroes VALUES (1, 'Pudge', '2004-01-01', TRUE), (2, 'Lina', '2004-01-01', FALSE);
            INSERT INTO skins VALUES (10, 'Dragonclaw Hook', 1, 950.0), (11, 'Fiery Soul', 2, 25.0), (12, 'Feast', 1, 27.0);
            CREATE INDEX idx_skins_hero ON skins (hero_id);
            SELECT name, price FROM skins WHERE hero_id = 1;
            """;

    private Database db;
    private QueryExecutor executor;

    private final JTextArea editor = new JTextArea(8, 60);
    private final ResultTableModel resultModel = new ResultTableModel();
    private final JTable resultTable = new JTable(resultModel);
    private final DefaultListModel<String> tablesModel = new DefaultListModel<>();
    private final JList<String> tablesList = new JList<>(tablesModel);
    private final JLabel status = new JLabel(" ");

    /**
     * Создает окно и открывает базу в указанной папке
     * @param dir папка базы данных
     */
    public MainWindow(Path dir) {
        super("mini-dbms");

        setJMenuBar(createMenu());
        setLayout(new BorderLayout());
        add(createTablesPanel(), BorderLayout.WEST);
        add(createCenter(), BorderLayout.CENTER);
        status.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        add(status, BorderLayout.SOUTH);

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                run(() -> {
                    closeDb();
                    dispose();
                });
            }
        });

        setSize(1000, 650);
        setLocationRelativeTo(null);
        run(() -> openDb(dir));
    }

    private JMenuBar createMenu() {
        JMenu file = new JMenu("Файл");
        file.add(menuItem("Открыть базу...", this::onOpen));
        file.addSeparator();
        file.add(menuItem("Выход", () -> dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING))));

        JMenu help = new JMenu("Справка");
        help.add(menuItem("Пример запросов", () -> editor.setText(EXAMPLE)));

        JMenuBar bar = new JMenuBar();
        bar.add(file);
        bar.add(help);
        return bar;
    }

    private JComponent createTablesPanel() {
        tablesList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tablesList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                String table = tablesList.getSelectedValue();
                if (e.getClickCount() == 2 && table != null) {
                    editor.setText("SELECT * FROM " + table + ";");
                    run(MainWindow.this::onExecute);
                }
            }
        });
        JScrollPane scroll = new JScrollPane(tablesList);
        scroll.setBorder(BorderFactory.createTitledBorder("Таблицы"));
        scroll.setPreferredSize(new Dimension(170, 0));
        return scroll;
    }

    private JComponent createCenter() {
        editor.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        // Ctrl+Enter (Cmd+Enter на Mac) — выполнить
        int mask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        editor.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, mask), "execute");
        editor.getActionMap().put("execute", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                run(MainWindow.this::onExecute);
            }
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(button("Выполнить", this::onExecute));
        buttons.add(button("Очистить", () -> editor.setText("")));
        buttons.add(new JLabel("Ctrl+Enter — выполнить; если текст выделен, выполняется только он"));

        JPanel top = new JPanel(new BorderLayout());
        top.add(new JScrollPane(editor), BorderLayout.CENTER);
        top.add(buttons, BorderLayout.SOUTH);

        resultTable.setAutoCreateRowSorter(true);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, top, new JScrollPane(resultTable));
        split.setResizeWeight(0.35);
        return split;
    }

    private JMenuItem menuItem(String text, DbAction action) {
        JMenuItem item = new JMenuItem(text);
        item.addActionListener(e -> run(action));
        return item;
    }

    private JButton button(String text, DbAction action) {
        JButton b = new JButton(text);
        b.addActionListener(e -> run(action));
        return b;
    }

    /** Показывает ошибку в диалоге */
    private void run(DbAction action) {
        try {
            action.run();
        } catch (DbException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Ошибка ввода-вывода: " + ex.getMessage(),
                    "Ошибка", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openDb(Path dir) throws Exception {
        closeDb();
        db = Database.open(dir);
        executor = new QueryExecutor(db);
        setTitle("mini-dbms — " + dir.toAbsolutePath());
        refreshTables();
        resultModel.clear();
        status.setText("База открыта: " + dir.toAbsolutePath());
        if (db.wasRecovered()) {
            JOptionPane.showMessageDialog(this,
                    "Прошлый запуск завершился аварийно. Индексы перестроены по файлам таблиц.",
                    "Восстановление", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void closeDb() throws Exception {
        if (db != null) {
            db.close();
            db = null;
            executor = null;
        }
    }

    private void refreshTables() {
        tablesModel.clear();
        for (TableSchema t : db.catalog().all()) {
            tablesModel.addElement(t.getName());
        }
    }

    private void onOpen() throws Exception {
        JFileChooser ch = new JFileChooser(new File("."));
        ch.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        ch.setDialogTitle("Папка базы данных");
        if (ch.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            openDb(ch.getSelectedFile().toPath());
        }
    }

    /** Выполняет выделенный текст или весь текст редактора */
    private void onExecute() throws Exception {
        if (executor == null) {
            throw new DbException("База не открыта");
        }
        String text = editor.getSelectedText() != null ? editor.getSelectedText() : editor.getText();
        List<String> queries = SqlScript.split(text);
        if (queries.isEmpty()) {
            throw new DbException("Введите запрос");
        }

        long start = System.nanoTime();
        QueryResult last = null;
        int done = 0;
        try {
            for (String q : queries) {
                last = executor.execute(q);
                done++;
                if (!last.columns().isEmpty()) {
                    resultModel.setResult(last);
                }
            }
        } catch (DbException e) {
            throw new DbException("Запрос " + (done + 1) + " из " + queries.size() + ": " + e.getMessage());
        } finally {
            refreshTables();
        }
        double ms = (System.nanoTime() - start) / 1_000_000.0;
        status.setText(String.format("Выполнено запросов: %d. %s — %.1f мс", done, last.message(), ms));
    }
}