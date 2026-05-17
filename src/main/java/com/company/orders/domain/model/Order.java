package com.company.orders.domain.model;

import com.company.orders.domain.exception.InvalidOrderStateException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * AGGREGATE ROOT — Order. Zero framework dependencies. Pure Java 25.
 *
 * <p>
 * All business rules live here: - Minimum one item required - PENDING → CONFIRMED → SHIPPED →
 * DELIVERED - PENDING or CONFIRMED → CANCELLED
 */
public class Order {

    private final String orderId;
    private final String customerId;
    private final List<OrderItem> items;
    private OrderStatus status;
    private Money totalAmount;
    private final Instant createdAt;
    private Instant updatedAt;

    // ── Factory ──────────────────────────────────────────────────────────────
    public static Order create(String customerId, List<OrderItem> items) {
        if (customerId == null || customerId.isBlank())
            throw new IllegalArgumentException("customerId is required");
        if (items == null || items.isEmpty())
            throw new IllegalArgumentException("Order must have at least one item");

        var total = items.stream().map(OrderItem::getSubtotal).reduce(Money.ZERO, Money::add);

        return new Order(
                UUID.randomUUID().toString(),
                customerId,
                new ArrayList<>(items),
                OrderStatus.PENDING,
                total,
                Instant.now(),
                Instant.now());
    }

    // ── Reconstitution constructor (infrastructure mapper only) ───────────────
    public Order(
            String orderId,
            String customerId,
            List<OrderItem> items,
            OrderStatus status,
            Money totalAmount,
            Instant createdAt,
            Instant updatedAt) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.items = new ArrayList<>(items);
        this.status = status;
        this.totalAmount = totalAmount;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // ── Business behaviours ───────────────────────────────────────────────────

    public void confirm() {
        if (status != OrderStatus.PENDING)
            throw new InvalidOrderStateException(
                    "Only PENDING orders can be confirmed. Current: " + status);
        status = OrderStatus.CONFIRMED;
        updatedAt = Instant.now();
    }

    public void cancel(String reason) {
        if (!status.isCancellable())
            throw new InvalidOrderStateException("Cannot cancel an order in status: " + status);
        status = OrderStatus.CANCELLED;
        updatedAt = Instant.now();
    }

    public void markShipped() {
        if (status != OrderStatus.CONFIRMED)
            throw new InvalidOrderStateException(
                    "Only CONFIRMED orders can be shipped. Current: " + status);
        status = OrderStatus.SHIPPED;
        updatedAt = Instant.now();
    }

    // ── Getters ───────────────────────────────────────────────────────────────
    public String getOrderId() {
        return orderId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public OrderStatus getStatus() {
        return status;
    }

    public Money getTotalAmount() {
        return totalAmount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
