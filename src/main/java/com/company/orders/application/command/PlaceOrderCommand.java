package com.company.orders.application.command;

import com.company.orders.bus.command.Command;
import com.company.orders.domain.model.Order;
import java.util.List;

public record PlaceOrderCommand(String customerId, List<OrderItemCmd> items)
    implements Command<Order> {
  public record OrderItemCmd(
      String productId, String productName, int quantity, double unitPrice, String currency) {}
}
