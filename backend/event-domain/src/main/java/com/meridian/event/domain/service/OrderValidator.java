package com.meridian.event.domain.service;

import com.meridian.event.domain.exception.DomainException;
import com.meridian.event.domain.model.Order;
import com.meridian.event.domain.model.OrderLine;
import com.meridian.event.domain.model.valueobjects.Money;
import com.meridian.event.domain.model.valueobjects.Sku;

import java.util.Objects;

public class OrderValidator {

    public ValidationResult validate(Order order) {
        Objects.requireNonNull(order, "order cannot be null");

        if (order.getLines().isEmpty()) {
            return ValidationResult.invalid("Order must contain at least one line item");
        }

        for (OrderLine line : order.getLines()) {
            if (line.getQuantity() <= 0) {
                return ValidationResult.invalid("Invalid quantity for SKU: " + line.getSku().value());
            }
            if (line.getUnitPrice().value().compareTo(Money.zero().value()) < 0) {
                return ValidationResult.invalid("Unit price cannot be negative for SKU: " + line.getSku().value());
            }
        }

        return ValidationResult.valid();
    }

    public record ValidationResult(boolean valid, String errorMessage) {
        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult invalid(String errorMessage) {
            return new ValidationResult(false, errorMessage);
        }
    }
}
