package com.company.orders.infrastructure.persistence;

import com.company.orders.domain.model.Money;
import com.company.orders.domain.model.Order;
import com.company.orders.domain.model.OrderItem;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * INFRASTRUCTURE — Repository
 *
 * <p>
 * Single repository used by both command and query handlers. Converts between the clean domain
 * model and the JPA persistence model.
 *
 * <p>
 * With virtual threads (spring.threads.virtual.enabled=true), all JDBC blocking calls here are
 * safely parked — the virtual thread yields the carrier thread while waiting for the DB response,
 * enabling high concurrency with a small connection pool.
 */
@Repository
public class OrderRepository {

    private final OrderJpaRepository jpa;

    public OrderRepository(OrderJpaRepository jpa) {
        this.jpa = jpa;
    }

    public Order save(Order order) {
        return toDomain(jpa.save(toEntity(order)));
    }

    public Optional<Order> findById(String orderId) {
        return jpa.findByIdWithItems(orderId).map(this::toDomain);
    }

    public List<Order> findByCustomerId(String customerId) {
        return jpa.findByCustomerIdWithItems(customerId).stream().map(this::toDomain).toList();
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

    private OrderJpaEntity toEntity(Order o) {
        var entity = new OrderJpaEntity(
                o.getOrderId(),
                o.getCustomerId(),
                o.getStatus(),
                o.getTotalAmount().getAmount(),
                o.getTotalAmount().getCurrency(),
                o.getCreatedAt(),
                o.getUpdatedAt());
        o.getItems()
                .forEach(
                        item -> entity.addItem(
                                new OrderItemJpaEntity(
                                        item.getItemId(),
                                        item.getProductId(),
                                        item.getProductName(),
                                        item.getQuantity(),
                                        item.getUnitPrice().getAmount(),
                                        item.getUnitPrice().getCurrency())));
        return entity;
    }

    private Order toDomain(OrderJpaEntity e) {
        var items = e.getItems().stream()
                .map(
                        i -> new OrderItem(
                                i.getProductId(),
                                i.getProductName(),
                                i.getQuantity(),
                                new Money(i.getUnitPrice(), i.getCurrency())))
                .toList();
        return new Order(
                e.getOrderId(),
                e.getCustomerId(),
                items,
                e.getStatus(),
                new Money(e.getTotalAmount(), e.getCurrency()),
                e.getCreatedAt(),
                e.getUpdatedAt());
    }
}
