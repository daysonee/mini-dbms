package ru.daysonee.dbms;

import ru.daysonee.dbms.engine.Database;
import ru.daysonee.dbms.engine.QueryExecutor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Random;
import java.util.stream.Stream;

/**
 * Замеры производительности: одни и те же запросы на таблицах разного размера без индекса и с индексом

 */
public final class Benchmark {
    private static final int[] SIZES = {1_000, 10_000, 50_000};
    private static final int FAST_QUERIES = 1_000;
    private static final int SLOW_QUERIES = 20;

    private Benchmark() {
    }

    /**
     * Запускает замеры
     *
     * @param args не используются
     * @throws IOException при ошибке работы с файлами
     */
    public static void main(String[] args) throws IOException {
        run(1_000, false);

        System.out.println("| Строк | Вставка, мкс/строка | `id =` (индекс ключа), мс | `name =` без индекса, мс "
                + "| `name =` с индексом, мс | Ускорение | `price >` (перебор), мс |");
        System.out.println("|---:|---:|---:|---:|---:|---:|---:|");
        for (int n : SIZES) {
            run(n, true);
        }
    }

    /**
     * Создаёт таблицу из n строк во временной папке, замеряет запросы и удаляет папку
     *
     * @param n число строк
     * @param print печатать ли строку таблицы результатов
     * @throws IOException при ошибке работы с файлами
     */
    private static void run(int n, boolean print) throws IOException {
        Path dir = Files.createTempDirectory("bench");
        Random rnd = new Random(42);
        try (Database db = Database.open(dir)) {
            QueryExecutor ex = new QueryExecutor(db);
            ex.execute("CREATE TABLE skins (id INT PRIMARY KEY, name STRING(20), price DOUBLE)");

            long t = System.nanoTime();
            for (int id = 1; id <= n; id++) {
                ex.execute("INSERT INTO skins VALUES (" + id + ", 'skin" + id + "', " + rnd.nextInt(10_000) + ".5)");
            }
            double insertUs = (System.nanoTime() - t) / 1000.0 / n;

            t = System.nanoTime();
            for (int i = 0; i < FAST_QUERIES; i++) {
                ex.execute("SELECT * FROM skins WHERE id = " + (1 + rnd.nextInt(n)));
            }
            double byIdMs = (System.nanoTime() - t) / 1e6 / FAST_QUERIES;

            t = System.nanoTime();
            for (int i = 0; i < SLOW_QUERIES; i++) {
                ex.execute("SELECT * FROM skins WHERE name = 'skin" + (1 + rnd.nextInt(n)) + "'");
            }
            double noIndexMs = (System.nanoTime() - t) / 1e6 / SLOW_QUERIES;

            ex.execute("CREATE INDEX idx_skins_name ON skins (name)");
            t = System.nanoTime();
            for (int i = 0; i < FAST_QUERIES; i++) {
                ex.execute("SELECT * FROM skins WHERE name = 'skin" + (1 + rnd.nextInt(n)) + "'");
            }
            double withIndexMs = (System.nanoTime() - t) / 1e6 / FAST_QUERIES;


            t = System.nanoTime();
            for (int i = 0; i < SLOW_QUERIES; i++) {
                ex.execute("SELECT id FROM skins WHERE price > 9990");
            }
            double rangeMs = (System.nanoTime() - t) / 1e6 / SLOW_QUERIES;

            if (print) {
                System.out.printf("| %,d | %.1f | %.3f | %.2f | %.3f | ×%.0f | %.2f |%n",
                        n, insertUs, byIdMs, noIndexMs, withIndexMs, noIndexMs / withIndexMs, rangeMs);
            }
        } finally {
            deleteRecursively(dir);
        }
    }

    /**
     * Удаляет папку со всем содержимым
     *
     * @param dir папка
     * @throws IOException если файлы не удалось удалить
     */
    private static void deleteRecursively(Path dir) throws IOException {
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) {   // сначала файлы, потом папка
                Files.delete(p);
            }
        }
    }
}
