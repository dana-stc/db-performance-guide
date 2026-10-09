package com.example.dbperf.lessons;

import com.example.dbperf.util.Timing;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Every round trip to the database has a fixed cost (network, parsing, transaction commit).
 * Doing 20,000 tiny operations costs far more than doing them as one batch.
 */
@Component
@Order(1)
public class Lesson1Batching implements Lesson {

    private static final int ROWS = 20_000;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public Lesson1Batching(JdbcTemplate jdbc, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
    }

    @Override public int number() { return 1; }
    @Override public String title() { return "Fewer round trips: batching inserts"; }

    @Override
    public void run() {
        String sql = "INSERT INTO import_test (id, label) VALUES (?, ?)";

        // BAD: one statement and one commit per row (auto-commit)
        jdbc.update("DELETE FROM import_test");
        double slow = Timing.onceMs(() -> {
            for (int i = 1; i <= ROWS; i++) {
                jdbc.update(sql, i, "value-" + i);
            }
        });

        // GOOD: one transaction, rows sent in batches
        jdbc.update("DELETE FROM import_test");
        double fast = Timing.onceMs(() -> tx.executeWithoutResult(s -> {
            List<Object[]> batch = new ArrayList<>();
            for (int i = 1; i <= ROWS; i++) {
                batch.add(new Object[]{i, "value-" + i});
            }
            jdbc.batchUpdate(sql, batch);
        }));

        System.out.println("Insert " + ROWS + " rows");
        System.out.println("  one by one (auto-commit): " + Timing.fmt(slow));
        System.out.println("  batched, one transaction: " + Timing.fmt(fast) + "   -> " + Timing.speedup(slow, fast));
        System.out.println("(With a REAL database over a network the gap is much bigger, because every round trip costs ~1 ms.)");
    }
}
