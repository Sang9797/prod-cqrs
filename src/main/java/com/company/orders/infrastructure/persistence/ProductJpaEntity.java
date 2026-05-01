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
import java.time.Instant;

@Entity
@Table(
    name = "products",
    indexes = {
      @Index(name = "idx_products_category_id", columnList = "category_id"),
      @Index(name = "idx_products_is_active", columnList = "is_active")
    })
public class ProductJpaEntity {

  @Id
  @Column(name = "product_id", length = 36)
  private String productId;

  @Column(nullable = false, length = 50, unique = true)
  private String sku;

  @Column(nullable = false, length = 255)
  private String name;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "category_id", nullable = false)
  private ProductCategoryJpaEntity category;

  @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
  private BigDecimal unitPrice;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(name = "is_active", nullable = false)
  private boolean active;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public ProductJpaEntity() {}

  public ProductJpaEntity(
      String productId,
      String sku,
      String name,
      ProductCategoryJpaEntity category,
      BigDecimal unitPrice,
      String currency,
      boolean active,
      Instant createdAt,
      Instant updatedAt) {
    this.productId = productId;
    this.sku = sku;
    this.name = name;
    this.category = category;
    this.unitPrice = unitPrice;
    this.currency = currency;
    this.active = active;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public String getProductId() {
    return productId;
  }

  public String getSku() {
    return sku;
  }

  public String getName() {
    return name;
  }

  public ProductCategoryJpaEntity getCategory() {
    return category;
  }

  public BigDecimal getUnitPrice() {
    return unitPrice;
  }

  public String getCurrency() {
    return currency;
  }

  public boolean isActive() {
    return active;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
