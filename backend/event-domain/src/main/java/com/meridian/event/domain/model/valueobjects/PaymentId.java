package com.meridian.event.domain.model.valueobjects;

import java.util.UUID;

public record PaymentId(String value) {
    public PaymentId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("PaymentId cannot be null or blank");
        }
    }

    public static PaymentId generate() {
        return new PaymentId(UUID.randomUUID().toString());
    }

    public static PaymentId from(String value) {
        return new PaymentId(value);
    }
}
