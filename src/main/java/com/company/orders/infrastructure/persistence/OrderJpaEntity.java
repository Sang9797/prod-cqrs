package com.company.orders.infrastructure.persistence;

import com.company.orders.domain.model.OrderStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
    name = "orders",
    indexes = {
      @Index(name = "idx_orders_customer_id", columnList = "customer_id"),
      @Index(name = "idx_orders_status", columnList = "status"),
      @Index(name = "idx_orders_created_at", columnList = "created_at")
    })
public class OrderJpaEntity {

  @Id
  @Column(name = "order_id", length = 36)
  private String orderId;

  @Column(name = "customer_id", nullable = false, length = 36)
  private String customerId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private OrderStatus status;

  @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
  private BigDecimal totalAmount;

  @Column(name = "currency", nullable = false, length = 3)
  private String currency;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @OneToMany(
      mappedBy = "order",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  private List<OrderItemJpaEntity> items = new ArrayList<>();

  public OrderJpaEntity() {}

  public OrderJpaEntity(
      String orderId,
      String customerId,
      OrderStatus status,
      BigDecimal totalAmount,
      String currency,
      Instant createdAt,
      Instant updatedAt) {
    this.orderId = orderId;
    this.customerId = customerId;
    this.status = status;
    this.totalAmount = totalAmount;
    this.currency = currency;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public void addItem(OrderItemJpaEntity item) {
    items.add(item);
    item.setOrder(this);
  }

  public String getOrderId() {
    return orderId;
  }

  public String getCustomerId() {
    return customerId;
  }

  public OrderStatus getStatus() {
    return status;
  }

  public BigDecimal getTotalAmount() {
    return totalAmount;
  }

  public String getCurrency() {
    return currency;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public List<OrderItemJpaEntity> getItems() {
    return items;
  }

  public void setStatus(OrderStatus s) {
    this.status = s;
  }

  public void setUpdatedAt(Instant t) {
    this.updatedAt = t;
  }
}
