package com.example.dbperf;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Fills the database with 2,000 customers and 300,000 orders. */
@Component
public class DataSeeder {

    public static final int CUSTOMERS = 2_000;
    public static final int ORDERS = 300_000;

    private static final String[] COUNTRIES = {"RO", "DE", "FR", "US", "UK", "IT", "ES"};
    private static final String[] STATUSES = {"NEW", "PAID", "SHIPPED", "DELIVERED", "CANCELLED"};

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public DataSeeder(JdbcTemplate jdbc, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
    }

    public void seed() {
        System.out.println("Seeding " + CUSTOMERS + " customers and " + ORDERS + " orders (one time)...");
        Random random = new Random(42); // fixed seed = same data on every run

        tx.executeWithoutResult(status -> {
            List<Object[]> customers = new ArrayList<>();
            for (long id = 1; id <= CUSTOMERS; id++) {
                customers.add(new Object[]{id, "Customer " + id, "customer" + id + "@example.com",
                        COUNTRIES[random.nextInt(COUNTRIES.length)]});
            }
            jdbc.batchUpdate("INSERT INTO customers (id, name, email, country) VALUES (?, ?, ?, ?)", customers);

            LocalDateTime start = LocalDateTime.of(2022, 1, 1, 0, 0);
            List<Object[]> orders = new ArrayList<>(10_000);
            for (long id = 1; id <= ORDERS; id++) {
                orders.add(new Object[]{
                        id,
                        1 + random.nextInt(CUSTOMERS),
                        STATUSES[random.nextInt(STATUSES.length)],
                        10 + random.nextInt(99_000) / 100.0,
                        Timestamp.valueOf(start.plusMinutes(random.nextInt(3 * 365 * 24 * 60)))});
                if (orders.size() == 10_000) {
                    jdbc.batchUpdate("INSERT INTO orders (id, customer_id, status, total, created_at) VALUES (?, ?, ?, ?, ?)", orders);
                    orders.clear();
                }
            }
        });
        System.out.println("Done. orders has " + jdbc.queryForObject("SELECT COUNT(*) FROM orders", Long.class) + " rows.");
    }
}
