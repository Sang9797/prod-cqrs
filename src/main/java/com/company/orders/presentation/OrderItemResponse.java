package com.company.orders.presentation;

import com.company.orders.domain.model.OrderItem;
import java.math.BigDecimal;

public record OrderItemResponse(
    String itemId,
    String productId,
    String productName,
    int quantity,
    BigDecimal unitPrice,
    BigDecimal subtotal,
    String currency) {
  public static OrderItemResponse from(OrderItem i) {
    return new OrderItemResponse(
        i.getItemId(),
        i.getProductId(),
        i.getProductName(),
        i.getQuantity(),
        i.getUnitPrice().getAmount(),
        i.getSubtotal().getAmount(),
        i.getUnitPrice().getCurrency());
  }
}
