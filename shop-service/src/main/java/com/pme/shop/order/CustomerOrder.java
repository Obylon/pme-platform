package com.pme.shop.order;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

@Entity
@Table(name = "customer_order")
public class CustomerOrder {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "order_line", joinColumns = @JoinColumn(name = "order_id"))
    private List<OrderLine> lines = new ArrayList<>();

    @Column(name = "total_cents", nullable = false)
    private long totalCents;

    @Column(name = "payment_reference", nullable = false, length = 80)
    private String paymentReference;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CustomerOrder() {
        // JPA
    }

    public static CustomerOrder confirmed(String userId, List<OrderLine> lines, String paymentReference, Instant now) {
        CustomerOrder order = new CustomerOrder();
        order.id = UUID.randomUUID();
        order.userId = userId;
        order.lines = new ArrayList<>(lines);
        order.totalCents = lines.stream().mapToLong(OrderLine::totalCents).sum();
        order.paymentReference = paymentReference;
        order.status = OrderStatus.CONFIRMED;
        order.createdAt = now;
        return order;
    }

    public UUID getId() { return id; }
    public String getUserId() { return userId; }
    public OrderStatus getStatus() { return status; }
    public List<OrderLine> getLines() { return List.copyOf(lines); }
    public long getTotalCents() { return totalCents; }
    public String getPaymentReference() { return paymentReference; }
    public Instant getCreatedAt() { return createdAt; }
}
