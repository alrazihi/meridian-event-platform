package com.meridian.event.domain.service;

import com.meridian.event.domain.exception.DomainException;
import com.meridian.event.domain.model.InventoryItem;
import com.meridian.event.domain.model.valueobjects.Sku;

import java.util.Map;
import java.util.Objects;

public class InventoryReserver {

    private final Map<Sku, InventoryItem> inventory;

    public InventoryReserver(Map<Sku, InventoryItem> inventory) {
        this.inventory = Objects.requireNonNull(inventory, "inventory cannot be null");
    }

    public ReservationResult reserve(Sku sku, int quantity) {
        Objects.requireNonNull(sku, "sku cannot be null");

        InventoryItem item = inventory.get(sku);
        if (item == null) {
            return ReservationResult.failed("SKU not found: " + sku.value());
        }

        try {
            item.reserve(quantity);
            return ReservationResult.successResult();
        } catch (DomainException e) {
            return ReservationResult.failed(e.getMessage());
        }
    }

    public record ReservationResult(boolean success, String errorMessage) {
        public static ReservationResult successResult() {
            return new ReservationResult(true, null);
        }

        public static ReservationResult failed(String errorMessage) {
            return new ReservationResult(false, errorMessage);
        }
    }
}

