package com.company.orders.application.command;

import com.company.orders.bus.command.Command;

public record CancelOrderCommand(String orderId, String reason) implements Command<Void> {}
