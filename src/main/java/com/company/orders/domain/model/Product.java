package com.company.orders.domain.model;

import java.time.Instant;
import java.util.Objects;

public class Product {

    private final String productId;
    private final String sku;
    private final String name;
    private final String categoryId;
    private final Money unitPrice;
    private final boolean active;
    private final Instant createdAt;

    public Product(
            String productId,
            String sku,
            String name,
            String categoryId,
            Money unitPrice,
            boolean active,
            Instant createdAt) {
        Objects.requireNonNull(productId, "productId required");
        Objects.requireNonNull(sku, "sku required");
        Objects.requireNonNull(name, "name required");
        Objects.requireNonNull(categoryId, "categoryId required");
        Objects.requireNonNull(unitPrice, "unitPrice required");
        this.productId = productId;
        this.sku = sku;
        this.name = name;
        this.categoryId = categoryId;
        this.unitPrice = unitPrice;
        this.active = active;
        this.createdAt = createdAt;
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

    public String getCategoryId() {
        return categoryId;
    }

    public Money getUnitPrice() {
        return unitPrice;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
