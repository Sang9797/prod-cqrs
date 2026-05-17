package com.company.orders.application.query;

public record LowStockItem(
        String productId,
        String sku,
        String productName,
        String warehouseId,
        String warehouseName,
        String region,
        int quantityAvailable,
        int quantityReserved,
        int quantityFree) {
}
