package com.company.orders.infrastructure.persistence;

import com.company.orders.domain.model.TransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(
    name = "inventory_transactions",
    indexes = {
      @Index(name = "idx_inv_tx_created_at", columnList = "created_at"),
      @Index(name = "idx_inv_tx_order_id", columnList = "order_id")
      // NOTE: No composite index on (product_id, warehouse_id) — intentional for k6 slow query
      // benchmarking.
      // To optimise: add @Index(name="idx_inv_tx_product_warehouse",
      //                         columnList="product_id,warehouse_id")
    })
public class InventoryTransactionJpaEntity {

  @Id
  @Column(name = "transaction_id", length = 36)
  private String transactionId;

  @Column(name = "product_id", nullable = false, length = 36)
  private String productId;

  @Column(name = "warehouse_id", nullable = false, length = 36)
  private String warehouseId;

  @Column(name = "order_id", length = 36)
  private String orderId;

  @Enumerated(EnumType.STRING)
  @Column(name = "transaction_type", nullable = false, length = 20)
  private TransactionType transactionType;

  @Column(name = "quantity_delta", nullable = false)
  private int quantityDelta;

  @Column(length = 500)
  private String notes;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  public InventoryTransactionJpaEntity() {}

  public InventoryTransactionJpaEntity(
      String transactionId,
      String productId,
      String warehouseId,
      String orderId,
      TransactionType transactionType,
      int quantityDelta,
      String notes,
      Instant createdAt) {
    this.transactionId = transactionId;
    this.productId = productId;
    this.warehouseId = warehouseId;
    this.orderId = orderId;
    this.transactionType = transactionType;
    this.quantityDelta = quantityDelta;
    this.notes = notes;
    this.createdAt = createdAt;
  }

  public String getTransactionId() {
    return transactionId;
  }

  public String getProductId() {
    return productId;
  }

  public String getWarehouseId() {
    return warehouseId;
  }

  public String getOrderId() {
    return orderId;
  }

  public TransactionType getTransactionType() {
    return transactionType;
  }

  public int getQuantityDelta() {
    return quantityDelta;
  }

  public String getNotes() {
    return notes;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
