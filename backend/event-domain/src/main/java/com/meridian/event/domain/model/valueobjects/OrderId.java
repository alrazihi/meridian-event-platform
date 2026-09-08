package com.meridian.event.domain.model.valueobjects;

import java.util.UUID;

public record OrderId(String value) {
    public OrderId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("OrderId cannot be null or blank");
        }
    }

    public static OrderId generate() {
        return new OrderId(UUID.randomUUID().toString());
    }

    public static OrderId from(String value) {
        return new OrderId(value);
    }
}
