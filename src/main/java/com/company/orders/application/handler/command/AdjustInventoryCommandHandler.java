package com.company.orders.application.handler.command;

import com.company.orders.application.command.AdjustInventoryCommand;
import com.company.orders.bus.command.CommandHandler;
import com.company.orders.domain.exception.ProductNotFoundException;
import com.company.orders.domain.model.Inventory;
import com.company.orders.domain.model.TransactionType;
import com.company.orders.infrastructure.persistence.InventoryRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public class AdjustInventoryCommandHandler implements CommandHandler<AdjustInventoryCommand, Void> {

    private static final Logger LOG = LoggerFactory.getLogger(AdjustInventoryCommandHandler.class);

    private final InventoryRepository repository;
    private final Counter inventoryAdjustments;

    public AdjustInventoryCommandHandler(
            InventoryRepository repository, MeterRegistry meterRegistry) {
        this.repository = repository;
        this.inventoryAdjustments = Counter.builder("inventory.adjustments.total")
                .description("Total number of inventory adjustments")
                .register(meterRegistry);
    }

    @Override
    public Class<AdjustInventoryCommand> commandType() {
        return AdjustInventoryCommand.class;
    }

    @Override
    public Void handle(AdjustInventoryCommand cmd) {
        LOG.info(
                "[AdjustInventory] product={} warehouse={} delta={}",
                cmd.productId(),
                cmd.warehouseId(),
                cmd.delta());

        Inventory inv = repository
                .findByProductAndWarehouse(cmd.productId(), cmd.warehouseId())
                .orElseThrow(() -> new ProductNotFoundException(cmd.productId()));

        inv.adjust(cmd.delta());
        repository.save(inv);
        repository.recordTransaction(
                cmd.productId(),
                cmd.warehouseId(),
                TransactionType.ADJUST,
                cmd.delta(),
                null,
                cmd.reason());
        inventoryAdjustments.increment();
        LOG.info("[AdjustInventory] adjusted delta={}", cmd.delta());
        return null;
    }
}
