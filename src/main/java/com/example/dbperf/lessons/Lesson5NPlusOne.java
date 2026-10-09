package com.example.dbperf.lessons;

import com.example.dbperf.domain.OrderEntity;
import com.example.dbperf.util.Timing;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * The famous N+1 problem (ORMs like JPA/Hibernate):
 *   1 query loads N orders, then 1 extra query PER order loads its customer = N+1 queries.
 * Fix: tell the ORM to load the customer in the same query (JOIN FETCH).
 */
@Component
@Order(5)
public class Lesson5NPlusOne implements Lesson {

    private final EntityManager em;
    private final EntityManagerFactory emf;
    private final TransactionTemplate tx;

    public Lesson5NPlusOne(EntityManager em, EntityManagerFactory emf, TransactionTemplate tx) {
        this.em = em;
        this.emf = emf;
        this.tx = tx;
    }

    @Override public int number() { return 5; }
    @Override public String title() { return "The N+1 query problem"; }

    @Override
    public void run() {
        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();

        // BAD
        em.clear();
        stats.clear();
        double badMs = Timing.onceMs(() -> tx.executeWithoutResult(s -> {
            List<OrderEntity> orders = em.createQuery(
                    "SELECT o FROM OrderEntity o WHERE o.id <= 200 ORDER BY o.id", OrderEntity.class).getResultList();
            for (OrderEntity o : orders) {
                o.getCustomer().getName(); // touching the lazy customer triggers one more SELECT
            }
        }));
        long badQueries = stats.getPrepareStatementCount();

        // GOOD
        em.clear();
        stats.clear();
        double goodMs = Timing.onceMs(() -> tx.executeWithoutResult(s -> {
            List<OrderEntity> orders = em.createQuery(
                    "SELECT o FROM OrderEntity o JOIN FETCH o.customer WHERE o.id <= 200 ORDER BY o.id", OrderEntity.class).getResultList();
            for (OrderEntity o : orders) {
                o.getCustomer().getName(); // already loaded, no extra query
            }
        }));
        long goodQueries = stats.getPrepareStatementCount();

        System.out.println("Load 200 orders and print each customer's name");
        System.out.println("  lazy loading (N+1): " + badQueries + " SQL queries, " + Timing.fmt(badMs));
        System.out.println("  JOIN FETCH:         " + goodQueries + " SQL query,   " + Timing.fmt(goodMs));
        System.out.println("\nIn this in-memory demo each query is nearly free. Over a real network, "
                + badQueries + " round trips instead of " + goodQueries + " is a big difference.");
    }
}
