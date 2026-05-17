package com.company.orders.application.command;

import com.company.orders.bus.command.Command;

public record ReserveInventoryCommand(
        String productId, String warehouseId, int quantity,
        String orderId) implements Command<Void> {
}
