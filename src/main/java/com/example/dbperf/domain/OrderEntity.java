package com.example.dbperf.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
public class OrderEntity {

    @Id
    private Long id;

    // LAZY = the customer is loaded only when you actually touch it (that is where N+1 comes from)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private CustomerEntity customer;

    private String status;
    private BigDecimal total;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    protected OrderEntity() {
    }

    public Long getId() { return id; }
    public CustomerEntity getCustomer() { return customer; }
    public String getStatus() { return status; }
    public BigDecimal getTotal() { return total; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
