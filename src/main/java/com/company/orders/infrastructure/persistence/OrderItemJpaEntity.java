package com.company.orders.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(
    name = "order_items",
    indexes = @Index(name = "idx_order_items_order_id", columnList = "order_id"))
public class OrderItemJpaEntity {

  @Id
  @Column(name = "item_id", length = 36)
  private String itemId;

  @Column(name = "product_id", nullable = false, length = 36)
  private String productId;

  @Column(name = "product_name", nullable = false)
  private String productName;

  @Column(nullable = false)
  private int quantity;

  @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
  private BigDecimal unitPrice;

  @Column(name = "currency", nullable = false, length = 3)
  private String currency;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "order_id", nullable = false)
  private OrderJpaEntity order;

  public OrderItemJpaEntity() {}

  public OrderItemJpaEntity(
      String itemId,
      String productId,
      String productName,
      int quantity,
      BigDecimal unitPrice,
      String currency) {
    this.itemId = itemId;
    this.productId = productId;
    this.productName = productName;
    this.quantity = quantity;
    this.unitPrice = unitPrice;
    this.currency = currency;
  }

  public String getItemId() {
    return itemId;
  }

  public String getProductId() {
    return productId;
  }

  public String getProductName() {
    return productName;
  }

  public int getQuantity() {
    return quantity;
  }

  public BigDecimal getUnitPrice() {
    return unitPrice;
  }

  public String getCurrency() {
    return currency;
  }

  public void setOrder(OrderJpaEntity o) {
    this.order = o;
  }
}
