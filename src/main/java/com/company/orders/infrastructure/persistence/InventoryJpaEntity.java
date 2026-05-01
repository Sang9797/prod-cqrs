package com.company.orders.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(
    name = "inventory",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_inventory_product_warehouse",
            columnNames = {"product_id", "warehouse_id"}),
    indexes = {
      @Index(name = "idx_inventory_product_id", columnList = "product_id"),
      @Index(name = "idx_inventory_warehouse_id", columnList = "warehouse_id")
    })
public class InventoryJpaEntity {

  @Id
  @Column(name = "inventory_id", length = 36)
  private String inventoryId;

  @Column(name = "product_id", nullable = false, length = 36)
  private String productId;

  @Column(name = "warehouse_id", nullable = false, length = 36)
  private String warehouseId;

  @Column(name = "quantity_available", nullable = false)
  private int quantityAvailable;

  @Column(name = "quantity_reserved", nullable = false)
  private int quantityReserved;

  @Column(name = "last_updated", nullable = false)
  private Instant lastUpdated;

  public InventoryJpaEntity() {}

  public InventoryJpaEntity(
      String inventoryId,
      String productId,
      String warehouseId,
      int quantityAvailable,
      int quantityReserved,
      Instant lastUpdated) {
    this.inventoryId = inventoryId;
    this.productId = productId;
    this.warehouseId = warehouseId;
    this.quantityAvailable = quantityAvailable;
    this.quantityReserved = quantityReserved;
    this.lastUpdated = lastUpdated;
  }

  public String getInventoryId() {
    return inventoryId;
  }

  public String getProductId() {
    return productId;
  }

  public String getWarehouseId() {
    return warehouseId;
  }

  public int getQuantityAvailable() {
    return quantityAvailable;
  }

  public int getQuantityReserved() {
    return quantityReserved;
  }

  public Instant getLastUpdated() {
    return lastUpdated;
  }

  public void setQuantityAvailable(int qty) {
    this.quantityAvailable = qty;
  }

  public void setQuantityReserved(int qty) {
    this.quantityReserved = qty;
  }

  public void setLastUpdated(Instant t) {
    this.lastUpdated = t;
  }
}
