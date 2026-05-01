package com.company.orders.application.command;

import com.company.orders.bus.command.Command;

public record ConfirmOrderCommand(String orderId) implements Command<Void> {}
