package com.company.orders.presentation;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(String error, String message, List<String> details, Instant timestamp) {
  public static ErrorResponse of(String error, String message) {
    return new ErrorResponse(error, message, List.of(), Instant.now());
  }

  public static ErrorResponse of(String error, String message, List<String> details) {
    return new ErrorResponse(error, message, details, Instant.now());
  }
}
