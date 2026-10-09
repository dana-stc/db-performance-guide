package com.example.dbperf.lessons;

import com.example.dbperf.DataSeeder;
import com.example.dbperf.util.Timing;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * An index is like the index at the back of a book: instead of reading every page (full table scan)
 * the database jumps straight to the right rows.
 */
@Component
@Order(2)
public class Lesson2Indexes implements Lesson {

    private final JdbcTemplate jdbc;

    public Lesson2Indexes(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override public int number() { return 2; }
    @Override public String title() { return "Indexes and EXPLAIN"; }

    @Override
    public void run() {
        String query = "SELECT COUNT(*), SUM(total) FROM orders WHERE customer_id = ?";
        jdbc.execute("DROP INDEX IF EXISTS idx_orders_customer");

        double before = Timing.medianMs(7, () -> jdbc.queryForList(query, 1234));
        System.out.println("Query: " + query);
        System.out.println("\nWITHOUT index:  " + Timing.fmt(before));
        printPlan("SELECT * FROM orders WHERE customer_id = 1234");

        jdbc.execute("CREATE INDEX idx_orders_customer ON orders (customer_id)");

        double after = Timing.medianMs(7, () -> jdbc.queryForList(query, 1234));
        System.out.println("\nWITH index:     " + Timing.fmt(after) + "   -> " + Timing.speedup(before, after));
        printPlan("SELECT * FROM orders WHERE customer_id = 1234");

        // The price of an index: every INSERT/UPDATE must also update it
        jdbc.execute("DROP INDEX idx_orders_customer");
        jdbc.execute("DROP TABLE IF EXISTS write_test");
        jdbc.execute("CREATE TABLE write_test (id BIGINT PRIMARY KEY, a BIGINT, b BIGINT, c BIGINT)");
        double noIdx = Timing.onceMs(() -> insertRows(50_000));
        jdbc.update("DELETE FROM write_test");
        jdbc.execute("CREATE INDEX w_a ON write_test (a)");
        jdbc.execute("CREATE INDEX w_b ON write_test (b)");
        jdbc.execute("CREATE INDEX w_c ON write_test (c)");
        double withIdx = Timing.onceMs(() -> insertRows(50_000));
        jdbc.execute("DROP TABLE write_test");

        System.out.println("\nThe price of indexes: inserting 50,000 rows");
        System.out.println("  no extra index:  " + Timing.fmt(noIdx));
        System.out.println("  3 extra indexes: " + Timing.fmt(withIdx) + "   (writes are slower, and indexes use disk/memory)");

        // Keep the index for the following lessons
        jdbc.execute("CREATE INDEX idx_orders_customer ON orders (customer_id)");
    }

    private void insertRows(int count) {
        List<Object[]> rows = new java.util.ArrayList<>();
        for (int i = 1; i <= count; i++) {
            rows.add(new Object[]{i, i % 1000, i % 777, i % 333});
        }
        jdbc.batchUpdate("INSERT INTO write_test (id, a, b, c) VALUES (?, ?, ?, ?)", rows);
    }

    private void printPlan(String sql) {
        List<Map<String, Object>> rows = jdbc.queryForList("EXPLAIN " + sql);
        String plan = String.valueOf(rows.get(0).values().iterator().next()).replace("\n", " ").replaceAll("\\s+", " ");
        int where = plan.indexOf("/* ");
        System.out.println("  EXPLAIN says: " + (where >= 0 ? plan.substring(where) : plan));
    }
}
