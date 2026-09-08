package com.meridian.event.domain.model.valueobjects;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record Money(BigDecimal value, String currency) {
    public Money {
        Objects.requireNonNull(value, "value cannot be null");
        Objects.requireNonNull(currency, "currency cannot be null");
        if (value.scale() > 2) {
            throw new IllegalArgumentException("Money scale cannot exceed 2 decimal places");
        }
    }

    public static Money of(BigDecimal value, String currency) {
        return new Money(value.setScale(2, RoundingMode.HALF_UP), currency);
    }

    public static Money zero() {
        return new Money(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), "USD");
    }

    public Money add(Money other) {
        validateCurrency(other);
        return new Money(this.value.add(other.value), this.currency);
    }

    public Money multiply(int quantity) {
        return new Money(this.value.multiply(BigDecimal.valueOf(quantity)), this.currency);
    }

    private void validateCurrency(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException("Currency mismatch: " + this.currency + " vs " + other.currency);
        }
    }
}
