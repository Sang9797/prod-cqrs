package com.company.orders.presentation;

import com.company.orders.domain.model.Order;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
    String orderId,
    String customerId,
    String status,
    BigDecimal totalAmount,
    String currency,
    List<OrderItemResponse> items,
    Instant createdAt,
    Instant updatedAt) {
  public static OrderResponse from(Order o) {
    return new OrderResponse(
        o.getOrderId(),
        o.getCustomerId(),
        o.getStatus().name(),
        o.getTotalAmount().getAmount(),
        o.getTotalAmount().getCurrency(),
        o.getItems().stream().map(OrderItemResponse::from).toList(),
        o.getCreatedAt(),
        o.getUpdatedAt());
  }
}
