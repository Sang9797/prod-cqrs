package com.company.orders.application.query;

import java.math.BigDecimal;
import java.time.Instant;

public record InventoryReportItem(
        String parentCategoryName,
        String categoryName,
        String productId,
        String sku,
        String productName,
        BigDecimal unitPrice,
        String currency,
        String warehouseId,
        String warehouseName,
        String region,
        int quantityAvailable,
        int quantityReserved,
        int quantityFree,
        long totalReceived,
        long totalShipped,
        long transactionCount,
        Instant lastMovement) {
}
