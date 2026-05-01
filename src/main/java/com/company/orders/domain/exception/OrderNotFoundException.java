package com.company.orders.domain.exception;

public class OrderNotFoundException extends DomainException {
  public OrderNotFoundException(String id) {
    super("Order not found: " + id);
  }
}
