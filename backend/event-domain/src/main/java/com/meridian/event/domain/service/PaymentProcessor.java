package com.meridian.event.domain.service;

import com.meridian.event.domain.exception.DomainException;
import com.meridian.event.domain.model.Payment;
import com.meridian.event.domain.model.valueobjects.Money;

import java.util.Objects;

public class PaymentProcessor {

    public ProcessingResult process(Payment payment, Money amount) {
        Objects.requireNonNull(payment, "payment cannot be null");
        Objects.requireNonNull(amount, "amount cannot be null");

        if (!payment.getAmount().equals(amount)) {
            return ProcessingResult.failed("Payment amount mismatch: expected " + payment.getAmount() + " but got " + amount);
        }

        if (amount.value().compareTo(Money.zero().value()) <= 0) {
            return ProcessingResult.failed("Payment amount must be positive");
        }

        return ProcessingResult.successResult();
    }

    public record ProcessingResult(boolean success, String errorMessage) {
        public static ProcessingResult successResult() {
            return new ProcessingResult(true, null);
        }

        public static ProcessingResult failed(String errorMessage) {
            return new ProcessingResult(false, errorMessage);
        }
    }
}

