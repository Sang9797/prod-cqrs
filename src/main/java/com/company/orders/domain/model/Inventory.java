package com.company.orders.domain.model;

import com.company.orders.domain.exception.InsufficientInventoryException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** AGGREGATE ROOT — Inventory. Owns stock state for one product in one warehouse. */
public class Inventory {

    private final String inventoryId;
    private final String productId;
    private final String warehouseId;
    private int quantityAvailable;
    private int quantityReserved;
    private Instant lastUpdated;

    public static Inventory create(String productId, String warehouseId, int initialQuantity) {
        Objects.requireNonNull(productId, "productId required");
        Objects.requireNonNull(warehouseId, "warehouseId required");
        if (initialQuantity < 0)
            throw new IllegalArgumentException("initialQuantity cannot be negative");
        return new Inventory(
                UUID.randomUUID().toString(), productId, warehouseId, initialQuantity, 0,
                Instant.now());
    }

    public Inventory(
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

    public void reserve(int qty) {
        if (qty <= 0)
            throw new IllegalArgumentException("Reserve quantity must be positive");
        int free = quantityAvailable - quantityReserved;
        if (qty > free) {
            throw new InsufficientInventoryException(
                    String.format(
                            "Cannot reserve %d units of product %s in warehouse %s — only %d free",
                            qty, productId, warehouseId, free));
        }
        quantityReserved += qty;
        lastUpdated = Instant.now();
    }

    public void release(int qty) {
        if (qty <= 0)
            throw new IllegalArgumentException("Release quantity must be positive");
        quantityReserved = Math.max(0, quantityReserved - qty);
        lastUpdated = Instant.now();
    }

    public void adjust(int delta) {
        int newQty = quantityAvailable + delta;
        if (newQty < 0) {
            throw new InsufficientInventoryException(
                    String.format(
                            "Adjustment of %d would make stock negative for product %s "
                                    + "in warehouse %s",
                            delta, productId, warehouseId));
        }
        quantityAvailable = newQty;
        lastUpdated = Instant.now();
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

    public int getQuantityFree() {
        return quantityAvailable - quantityReserved;
    }

    public Instant getLastUpdated() {
        return lastUpdated;
    }
}
