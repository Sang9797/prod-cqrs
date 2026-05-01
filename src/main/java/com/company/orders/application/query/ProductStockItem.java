package com.company.orders.application.query;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductStockItem(
    String productId,
    String sku,
    String productName,
    BigDecimal unitPrice,
    String currency,
    String categoryName,
    String warehouseId,
    String warehouseName,
    String region,
    int quantityAvailable,
    int quantityReserved,
    int quantityFree,
    Instant lastUpdated) {}
