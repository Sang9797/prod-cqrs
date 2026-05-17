package com.company.orders.application.handler.command;

import com.company.orders.application.command.ReleaseInventoryCommand;
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
public class ReleaseInventoryCommandHandler
        implements
            CommandHandler<ReleaseInventoryCommand, Void> {

    private static final Logger LOG = LoggerFactory.getLogger(ReleaseInventoryCommandHandler.class);

    private final InventoryRepository repository;
    private final Counter inventoryReleases;

    public ReleaseInventoryCommandHandler(
            InventoryRepository repository, MeterRegistry meterRegistry) {
        this.repository = repository;
        this.inventoryReleases = Counter.builder("inventory.releases.total")
                .description("Total number of inventory releases")
                .register(meterRegistry);
    }

    @Override
    public Class<ReleaseInventoryCommand> commandType() {
        return ReleaseInventoryCommand.class;
    }

    @Override
    public Void handle(ReleaseInventoryCommand cmd) {
        LOG.info(
                "[ReleaseInventory] product={} warehouse={} qty={}",
                cmd.productId(),
                cmd.warehouseId(),
                cmd.quantity());

        Inventory inv = repository
                .findByProductAndWarehouse(cmd.productId(), cmd.warehouseId())
                .orElseThrow(() -> new ProductNotFoundException(cmd.productId()));

        inv.release(cmd.quantity());
        repository.save(inv);
        repository.recordTransaction(
                cmd.productId(),
                cmd.warehouseId(),
                TransactionType.RELEASE,
                cmd.quantity(),
                cmd.orderId(),
                "Released from order " + cmd.orderId());
        inventoryReleases.increment();
        LOG.info("[ReleaseInventory] released orderId={}", cmd.orderId());
        return null;
    }
}
