package com.company.orders.domain.exception;

public class WarehouseNotFoundException extends DomainException {
    public WarehouseNotFoundException(String warehouseId) {
        super("Warehouse not found: " + warehouseId);
    }
}
