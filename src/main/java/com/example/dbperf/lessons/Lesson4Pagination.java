package com.example.dbperf.lessons;

import com.example.dbperf.util.Timing;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * OFFSET pagination reads and throws away all the skipped rows, so page 10,000 is much slower than page 1.
 * Keyset pagination ("give me the 20 rows AFTER the last id I saw") uses the index and stays fast.
 */
@Component
@Order(4)
public class Lesson4Pagination implements Lesson {

    private final JdbcTemplate jdbc;

    public Lesson4Pagination(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override public int number() { return 4; }
    @Override public String title() { return "Pagination: OFFSET vs keyset"; }

    @Override
    public void run() {
        int pageSize = 20;
        int deepOffset = 280_000;

        double offsetFirst = Timing.medianMs(7, () ->
                jdbc.queryForList("SELECT * FROM orders ORDER BY id LIMIT ? OFFSET ?", pageSize, 0));
        double offsetDeep = Timing.medianMs(7, () ->
                jdbc.queryForList("SELECT * FROM orders ORDER BY id LIMIT ? OFFSET ?", pageSize, deepOffset));

        // Keyset: remember the last id of the previous page and continue after it
        long lastId = jdbc.queryForObject("SELECT id FROM orders ORDER BY id LIMIT 1 OFFSET ?", Long.class, deepOffset - 1);
        double keysetDeep = Timing.medianMs(7, () ->
                jdbc.queryForList("SELECT * FROM orders WHERE id > ? ORDER BY id LIMIT ?", lastId, pageSize));

        System.out.println("Page size " + pageSize + ", ordered by id");
        System.out.println("  OFFSET, first page:        " + Timing.fmt(offsetFirst));
        System.out.println("  OFFSET " + deepOffset + " (deep):  " + Timing.fmt(offsetDeep));
        System.out.println("  KEYSET at the same place:  " + Timing.fmt(keysetDeep) + "   -> " + Timing.speedup(offsetDeep, keysetDeep) + " than deep OFFSET");
        System.out.println("\nKeyset downside: you can go 'next/previous' but cannot jump straight to 'page 500'.");
    }
}
