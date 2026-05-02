package com.company.orders.application.handler.command;

import com.company.orders.application.command.ReserveInventoryCommand;
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
public class ReserveInventoryCommandHandler
    implements CommandHandler<ReserveInventoryCommand, Void> {

  private static final Logger LOG = LoggerFactory.getLogger(ReserveInventoryCommandHandler.class);

  private final InventoryRepository repository;
  private final Counter inventoryReservations;

  public ReserveInventoryCommandHandler(
      InventoryRepository repository, MeterRegistry meterRegistry) {
    this.repository = repository;
    this.inventoryReservations =
        Counter.builder("inventory.reservations.total")
            .description("Total number of inventory reservations")
            .register(meterRegistry);
  }

  @Override
  public Class<ReserveInventoryCommand> commandType() {
    return ReserveInventoryCommand.class;
  }

  @Override
  public Void handle(ReserveInventoryCommand cmd) {
    LOG.info(
        "[ReserveInventory] product={} warehouse={} qty={}",
        cmd.productId(),
        cmd.warehouseId(),
        cmd.quantity());

    Inventory inv =
        repository
            .findByProductAndWarehouse(cmd.productId(), cmd.warehouseId())
            .orElseThrow(() -> new ProductNotFoundException(cmd.productId()));

    inv.reserve(cmd.quantity());
    repository.save(inv);
    repository.recordTransaction(
        cmd.productId(),
        cmd.warehouseId(),
        TransactionType.RESERVE,
        -cmd.quantity(),
        cmd.orderId(),
        "Reserved for order " + cmd.orderId());
    inventoryReservations.increment();
    LOG.info("[ReserveInventory] reserved orderId={}", cmd.orderId());
    return null;
  }
}
