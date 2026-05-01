package com.company.orders.domain.exception;

public class InvalidOrderStateException extends DomainException {
  public InvalidOrderStateException(String msg) {
    super(msg);
  }
}
