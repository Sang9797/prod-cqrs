package com.company.orders.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Immutable value object. All arithmetic returns a NEW instance. */
public final class Money {

  public static final Money ZERO = new Money(BigDecimal.ZERO, "USD");

  private final BigDecimal amount;
  private final String currency;

  public Money(BigDecimal amount, String currency) {
    Objects.requireNonNull(amount, "amount required");
    Objects.requireNonNull(currency, "currency required");
    if (amount.compareTo(BigDecimal.ZERO) < 0)
      throw new IllegalArgumentException("Amount cannot be negative");
    this.amount = amount.setScale(2, RoundingMode.HALF_UP);
    this.currency = currency.toUpperCase();
  }

  public static Money of(double amount, String currency) {
    return new Money(BigDecimal.valueOf(amount), currency);
  }

  public Money add(Money other) {
    if (!this.currency.equals(other.currency))
      throw new IllegalArgumentException("Currency mismatch: " + currency + " / " + other.currency);
    return new Money(this.amount.add(other.amount), currency);
  }

  public Money multiply(int qty) {
    if (qty < 0) throw new IllegalArgumentException("qty cannot be negative");
    return new Money(amount.multiply(BigDecimal.valueOf(qty)), currency);
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public String getCurrency() {
    return currency;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof Money m)) return false;
    return amount.compareTo(m.amount) == 0 && currency.equals(m.currency);
  }

  @Override
  public int hashCode() {
    return Objects.hash(amount, currency);
  }

  @Override
  public String toString() {
    return amount.toPlainString() + " " + currency;
  }
}
