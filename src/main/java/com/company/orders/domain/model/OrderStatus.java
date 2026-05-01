package com.company.orders.domain.model;

public enum OrderStatus {
  PENDING,
  CONFIRMED,
  SHIPPED,
  DELIVERED,
  CANCELLED;

  public boolean isCancellable() {
    return this == PENDING || this == CONFIRMED;
  }
}
