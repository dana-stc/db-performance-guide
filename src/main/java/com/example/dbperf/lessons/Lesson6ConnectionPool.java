package com.example.dbperf.lessons;

import com.example.dbperf.util.Timing;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Opening a database connection is expensive (network, authentication, memory).
 * A connection POOL opens a few connections once and lends them out again and again.
 */
@Component
@Order(6)
public class Lesson6ConnectionPool implements Lesson {

    private static final String URL = "jdbc:h2:mem:perf;DB_CLOSE_DELAY=-1;QUERY_CACHE_SIZE=0";
    private static final int QUERIES = 2_000;

    @Override public int number() { return 6; }
    @Override public String title() { return "Connection pooling"; }

    @Override
    public void run() {
        double noPool = Timing.onceMs(() -> {
            for (int i = 0; i < QUERIES; i++) {
                try (Connection c = DriverManager.getConnection(URL, "sa", "")) { // new connection every time
                    query(c);
                } catch (SQLException e) {
                    throw new IllegalStateException(e);
                }
            }
        });

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(URL);
        config.setUsername("sa");
        config.setMaximumPoolSize(5);
        double withPool;
        try (HikariDataSource pool = new HikariDataSource(config)) {
            withPool = Timing.onceMs(() -> {
                for (int i = 0; i < QUERIES; i++) {
                    try (Connection c = pool.getConnection()) { // borrowed from the pool, "close" gives it back
                        query(c);
                    } catch (SQLException e) {
                        throw new IllegalStateException(e);
                    }
                }
            });
        }

        System.out.println(QUERIES + " tiny queries");
        System.out.println("  new connection each time: " + Timing.fmt(noPool));
        System.out.println("  HikariCP pool:            " + Timing.fmt(withPool) + "   -> " + Timing.speedup(noPool, withPool));
        System.out.println("\nSpring Boot already uses HikariCP by default. Tip: keep the pool SMALL (10-20 is plenty for most apps).");
    }

    private void query(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT 1")) {
            ps.executeQuery().close();
        }
    }
}
