package com.meridian.event.domain.model.valueobjects;

import java.util.Objects;

public record Sku(String value) {
    public Sku {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("SKU cannot be null or blank");
        }
    }

    public static Sku of(String value) {
        return new Sku(value.trim().toUpperCase());
    }
}
