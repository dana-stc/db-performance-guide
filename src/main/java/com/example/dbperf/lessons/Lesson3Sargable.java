package com.example.dbperf.lessons;

import com.example.dbperf.util.Timing;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Having an index is not enough: your query must be written so the database CAN use it.
 * Wrapping the indexed column in a function hides it from the index.
 */
@Component
@Order(3)
public class Lesson3Sargable implements Lesson {

    private final JdbcTemplate jdbc;

    public Lesson3Sargable(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override public int number() { return 3; }
    @Override public String title() { return "Write queries that can use the index"; }

    @Override
    public void run() {
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_orders_created ON orders (created_at)");

        String bad = "SELECT COUNT(*) FROM orders WHERE YEAR(created_at) = 2024 AND MONTH(created_at) = 3";
        String good = "SELECT COUNT(*) FROM orders WHERE created_at >= '2024-03-01' AND created_at < '2024-04-01'";

        double slow = Timing.medianMs(7, () -> jdbc.queryForObject(bad, Long.class));
        double fast = Timing.medianMs(7, () -> jdbc.queryForObject(good, Long.class));

        System.out.println("Both queries count the orders of March 2024. created_at has an index.\n");
        System.out.println("  function on the column:  " + Timing.fmt(slow) + "   " + bad);
        System.out.println("  plain range comparison:  " + Timing.fmt(fast) + "   " + good + "   -> " + Timing.speedup(slow, fast));
        System.out.println("\nThe first query must compute YEAR()/MONTH() for every row, so the index is useless.");
    }
}
