-- Deliberately NO index on orders.customer_id / created_at and NO foreign key:
-- the lessons add indexes themselves so you can see the difference.

CREATE TABLE customers (
    id      BIGINT PRIMARY KEY,
    name    VARCHAR(100) NOT NULL,
    email   VARCHAR(100) NOT NULL,
    country VARCHAR(2)   NOT NULL
);

CREATE TABLE orders (
    id          BIGINT PRIMARY KEY,
    customer_id BIGINT         NOT NULL,
    status      VARCHAR(20)    NOT NULL,
    total       DECIMAL(10, 2) NOT NULL,
    created_at  TIMESTAMP      NOT NULL
);

CREATE TABLE import_test (
    id    BIGINT PRIMARY KEY,
    label VARCHAR(50) NOT NULL
);
