package com.company.orders.infrastructure.persistence;

import com.company.orders.domain.exception.ProductNotFoundException;
import com.company.orders.domain.exception.WarehouseNotFoundException;
import com.company.orders.domain.model.Inventory;
import com.company.orders.domain.model.TransactionType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class InventoryRepository {

    private final InventoryJpaRepository inventoryJpa;
    private final InventoryTransactionJpaRepository transactionJpa;
    private final ProductJpaRepository productJpa;
    private final WarehouseJpaRepository warehouseJpa;

    public InventoryRepository(
            InventoryJpaRepository inventoryJpa,
            InventoryTransactionJpaRepository transactionJpa,
            ProductJpaRepository productJpa,
            WarehouseJpaRepository warehouseJpa) {
        this.inventoryJpa = inventoryJpa;
        this.transactionJpa = transactionJpa;
        this.productJpa = productJpa;
        this.warehouseJpa = warehouseJpa;
    }

    public Optional<Inventory> findByProductAndWarehouse(String productId, String warehouseId) {
        return inventoryJpa.findByProductIdAndWarehouseId(productId, warehouseId)
                .map(this::toDomain);
    }

    public Inventory save(Inventory inv) {
        InventoryJpaEntity entity = inventoryJpa
                .findByProductIdAndWarehouseId(inv.getProductId(), inv.getWarehouseId())
                .orElseGet(
                        () -> new InventoryJpaEntity(
                                inv.getInventoryId(),
                                inv.getProductId(),
                                inv.getWarehouseId(),
                                inv.getQuantityAvailable(),
                                inv.getQuantityReserved(),
                                inv.getLastUpdated()));
        entity.setQuantityAvailable(inv.getQuantityAvailable());
        entity.setQuantityReserved(inv.getQuantityReserved());
        entity.setLastUpdated(inv.getLastUpdated());
        return toDomain(inventoryJpa.save(entity));
    }

    public void recordTransaction(
            String productId,
            String warehouseId,
            TransactionType type,
            int delta,
            String orderId,
            String notes) {
        if (!productJpa.existsById(productId)) {
            throw new ProductNotFoundException(productId);
        }
        if (!warehouseJpa.existsById(warehouseId)) {
            throw new WarehouseNotFoundException(warehouseId);
        }
        transactionJpa.save(
                new InventoryTransactionJpaEntity(
                        UUID.randomUUID().toString(),
                        productId,
                        warehouseId,
                        orderId,
                        type,
                        delta,
                        notes,
                        Instant.now()));
    }

    private Inventory toDomain(InventoryJpaEntity e) {
        return new Inventory(
                e.getInventoryId(),
                e.getProductId(),
                e.getWarehouseId(),
                e.getQuantityAvailable(),
                e.getQuantityReserved(),
                e.getLastUpdated());
    }
}
