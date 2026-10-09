# Database Performance Guide (for beginners)

A hands-on introduction to the **most important ideas for making database access fast**.
Instead of only reading theory, you run small experiments on a table with **300,000 orders** and see the numbers yourself.

- Java 17 + Spring Boot, JPA/Hibernate, plain JDBC
- Uses **H2 in memory**: nothing to install, no Docker, no setup
- Six lessons, each one a short, commented class

---

## Table of contents
1. [The big picture](#1-the-big-picture)
2. [How to run it](#2-how-to-run-it)
3. [Lessons](#3-lessons)
4. [Other important topics](#4-other-important-topics-not-in-the-code)
5. [Checklist](#5-beginner-checklist)
6. [Project structure](#6-project-structure)

---

## 1. The big picture

A database request is slow for one of these reasons:

| Reason | Example | Lesson |
|---|---|---|
| The database reads **too much data** | Scanning all 300,000 rows to find 150 | 2, 3, 4 |
| Your app makes **too many requests** | 1 query per row instead of 1 query in total | 1, 5 |
| Each request has a **high fixed cost** | Opening a new connection every time | 6 |

> **Golden rule: measure first.** Do not guess what is slow. Look at the real queries, use `EXPLAIN`, and measure before and after every change.

### Words you will see
- **Query**: a request to the database (`SELECT ...`).
- **Full table scan**: the database reads every row to find matches. Slow on big tables.
- **Index**: an extra data structure (usually a B-tree) that lets the database jump straight to the rows it needs, like the index at the back of a book.
- **Execution plan** (`EXPLAIN`): the database tells you *how* it will run your query (table scan or index).
- **Round trip**: one request + response between your app and the database. Each one costs time, even if the query itself is instant.
- **Connection pool**: a set of already-open connections that your app borrows and returns.

---

## 2. How to run it

**Requirements:** Java 17+ and Maven.

```bash
mvn spring-boot:run
```

It creates the tables, inserts the data (a few seconds), then runs all lessons and prints the results.

Run only some lessons:
```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--lesson=2,5"
```

> Your numbers will differ from the ones in this README (different computer). Look at the **ratio** between "bad" and "good", not the exact milliseconds.
> H2 runs inside your app, so there is no network. With a real database (PostgreSQL, MySQL...) the round-trip lessons (1, 5, 6) matter even **more**.

---

## 3. Lessons

### Lesson 1 - Fewer round trips: batching
`Lesson1Batching`

**Problem.** Inserting 20,000 rows with 20,000 separate statements (each with its own commit) is slow.
Every statement means a round trip + a commit.

**Fix.** Send rows in a **batch** inside **one transaction**.
```java
jdbc.batchUpdate("INSERT INTO import_test (id, label) VALUES (?, ?)", rows);
```

Example result (in memory, so the gap is small):
```
one by one (auto-commit): 251 ms
batched, one transaction: 109 ms   -> ~2x faster
```
Against a real database server, where each round trip is ~1 ms, the same code is often **10-50x** faster.

With JPA, enable Hibernate batching: `spring.jpa.properties.hibernate.jdbc.batch_size=50`.

---

### Lesson 2 - Indexes and EXPLAIN
`Lesson2Indexes`

**Problem.** `SELECT ... FROM orders WHERE customer_id = 1234` on a table with no index on `customer_id`.
The database has no shortcut, so it reads **all 300,000 rows**. `EXPLAIN` shows this:
```
/* PUBLIC.ORDERS.tableScan */ WHERE CUSTOMER_ID = 1234
```

**Fix.**
```sql
CREATE INDEX idx_orders_customer ON orders (customer_id);
```
Now the plan says `PUBLIC.IDX_ORDERS_CUSTOMER: CUSTOMER_ID = 1234` - it uses the index.

```
WITHOUT index:  48.90 ms
WITH index:      0.27 ms   -> ~181x faster
```

**The catch: indexes are not free.**
Every `INSERT`/`UPDATE`/`DELETE` must also update every index, and indexes use memory and disk.
The lesson shows inserting 50,000 rows gets **~2x slower** with 3 extra indexes.

**Rules of thumb**
- Index columns used in `WHERE`, `JOIN ... ON`, and `ORDER BY`.
- Primary keys are indexed automatically. In many databases (not H2), foreign keys are not - index them yourself.
- Don't index everything. Skip columns with very few distinct values (like a yes/no flag) and tables that are tiny or write-heavy.
- **Composite index** `(a, b)` helps queries filtering on `a`, or on `a` and `b` - but not on `b` alone (column order matters).
- Always check with `EXPLAIN` that the database really uses your index.

---

### Lesson 3 - Write queries that can use the index
`Lesson3Sargable`

**Problem.** Having an index is not enough. If you wrap the column in a function, the database cannot use the index:
```sql
-- SLOW: must compute YEAR() and MONTH() for every row
WHERE YEAR(created_at) = 2024 AND MONTH(created_at) = 3

-- FAST: compares the raw column, so the index on created_at works
WHERE created_at >= '2024-03-01' AND created_at < '2024-04-01'
```
```
function on the column: 66.5 ms
plain range comparison:  3.0 ms   -> ~22x faster
```

Same idea for other patterns that hide a column from the index:
- `WHERE LOWER(email) = 'x'` (use a functional index, or store lowercase)
- `WHERE price + 10 > 100` (write `price > 90`)
- `WHERE name LIKE '%son'` (a leading `%` cannot use a normal index; `LIKE 'son%'` can)

---

### Lesson 4 - Pagination: OFFSET vs keyset
`Lesson4Pagination`

**Problem.** `LIMIT 20 OFFSET 280000` does **not** jump to row 280,000. The database reads and throws away 280,000 rows first. Deep pages get slower and slower.

**Fix: keyset (a.k.a. "seek") pagination.** Remember the last `id` of the previous page and ask for what comes after it:
```sql
SELECT * FROM orders WHERE id > :lastId ORDER BY id LIMIT 20
```
The index jumps directly to `lastId`, so every page costs the same.
```
OFFSET, first page:     0.30 ms
OFFSET 280000 (deep):  21.65 ms
KEYSET, same place:     0.15 ms   -> ~148x faster
```
Trade-off: you can do "next/previous" but not "jump to page 500". For infinite scroll and APIs, keyset is the better choice.

---

### Lesson 5 - The N+1 query problem
`Lesson5NPlusOne`, entities in `domain/`

**Problem.** The most common ORM (JPA/Hibernate) performance bug.
```java
List<OrderEntity> orders = repo.findAll...;        // 1 query
for (OrderEntity o : orders) {
    o.getCustomer().getName();                     // +1 query PER order (lazy loading)
}
```
For 200 orders this runs **189 queries** (1 for the orders + one for each different customer). With 10,000 orders it would be thousands.

**Fix.** Load what you need in the same query:
```java
"SELECT o FROM OrderEntity o JOIN FETCH o.customer WHERE ..."
```
```
lazy loading (N+1): 189 SQL queries
JOIN FETCH:           1 SQL query
```
Other fixes: `@EntityGraph`, `@BatchSize`, or a DTO projection that selects only the columns you need.

**How to spot it:** turn on SQL logging / Hibernate statistics (this project does: `hibernate.generate_statistics: true`) and look for the same query repeated many times.

---

### Lesson 6 - Connection pooling
`Lesson6ConnectionPool`

**Problem.** Opening a database connection is expensive (network handshake, authentication, memory on the server). Doing it for every query wastes time.

**Fix.** A **pool** opens a few connections once and lends them out. Calling `close()` on a pooled connection just gives it back.
```
new connection each time: 72 ms
HikariCP pool:            11 ms   -> ~7x faster
```
Spring Boot uses **HikariCP** automatically. The main setting is `spring.datasource.hikari.maximum-pool-size`.
**Keep it small.** A pool of 10-20 is enough for most apps; more connections usually make the database slower, not faster.

---

## 4. Other important topics (not in the code)

- **Select only what you need.** Avoid `SELECT *` on wide tables; fetch only the columns you use.
- **Caching.** Keep results of expensive or frequent reads in fast memory (Redis, Caffeine) and skip the database. See the companion project `redis-caching-demo`.
- **Normalization vs. denormalization.** Normalized tables avoid duplicate data; sometimes copying a value on purpose (denormalizing) avoids expensive joins on hot paths.
- **Transactions.** Keep them short. Long transactions hold locks and block others. Don't call remote services inside a transaction.
- **Read replicas.** Send reads to copies of the database to take load off the main one.
- **Partitioning / archiving.** Split or move old data so everyday queries touch less data.
- **Statistics & maintenance.** Databases choose plans using statistics; keep them fresh (`ANALYZE`) and watch for bloated tables.
- **Slow query log / monitoring.** Turn on your database's slow query log and find the worst queries with real data.

## 5. Beginner checklist

1. Is the query slow, or is my app making too many queries? (count them)
2. Run `EXPLAIN`. Do I see a full table scan on a big table?
3. Do `WHERE`, `JOIN` and `ORDER BY` columns have fitting indexes?
4. Does my query hide a column inside a function?
5. Am I loading more rows or columns than I show?
6. Am I paginating with a huge `OFFSET`?
7. Any N+1 in my ORM code?
8. Am I inserting/updating row by row instead of in batches?
9. Is the connection pool sized sensibly?
10. Could this read be cached?

## 6. Project structure
```
src/main/java/com/example/dbperf
├── DbPerformanceApplication.java   runs the seeder and then the lessons
├── DataSeeder.java                 creates 2,000 customers and 300,000 orders
├── domain/                         JPA entities (used by lesson 5)
├── lessons/                        Lesson1Batching ... Lesson6ConnectionPool
└── util/Timing.java                tiny helper: median timing of a task
src/main/resources
├── application.yml                 H2 in-memory, Hikari, Hibernate statistics
└── schema.sql                      tables WITHOUT extra indexes (lessons add them)
```

*Technical note:* the H2 URL contains `QUERY_CACHE_SIZE=0` to switch off H2's own result cache, otherwise repeating the same query would return instantly and hide the effect of indexes.
