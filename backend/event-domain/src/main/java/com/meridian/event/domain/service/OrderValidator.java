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
            return ValidationResult.invalidResult("Order must contain at least one line item");
        }

        for (OrderLine line : order.getLines()) {
            if (line.getQuantity() <= 0) {
                return ValidationResult.invalidResult("Invalid quantity for SKU: " + line.getSku().value());
            }
            if (line.getUnitPrice().value().compareTo(Money.zero().value()) < 0) {
                return ValidationResult.invalidResult("Unit price cannot be negative for SKU: " + line.getSku().value());
            }
        }

        return ValidationResult.validResult();
    }

    public record ValidationResult(boolean isValid, String errorMessage) {
        public static ValidationResult validResult() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult invalidResult(String errorMessage) {
            return new ValidationResult(false, errorMessage);
        }
    }
}


