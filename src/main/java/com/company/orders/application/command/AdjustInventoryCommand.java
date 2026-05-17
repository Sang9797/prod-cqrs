package com.company.orders.application.command;

import com.company.orders.bus.command.Command;

public record AdjustInventoryCommand(String productId, String warehouseId, int delta, String reason)
        implements
            Command<Void> {
}
