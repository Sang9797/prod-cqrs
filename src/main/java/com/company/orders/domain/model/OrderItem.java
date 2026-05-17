package com.company.orders.domain.model;

import java.util.Objects;
import java.util.UUID;

public class OrderItem {
    private final String itemId;
    private final String productId;
    private final String productName;
    private final int quantity;
    private final Money unitPrice;

    /** New item — generates a fresh itemId. */
    public OrderItem(String productId, String productName, int quantity, Money unitPrice) {
        if (quantity <= 0)
            throw new IllegalArgumentException("Quantity must be positive");
        Objects.requireNonNull(productId, "productId required");
        Objects.requireNonNull(unitPrice, "unitPrice required");
        this.itemId = UUID.randomUUID().toString();
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
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

    public Money getUnitPrice() {
        return unitPrice;
    }

    public Money getSubtotal() {
        return unitPrice.multiply(quantity);
    }
}
