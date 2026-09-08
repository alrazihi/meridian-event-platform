package com.meridian.event.domain.model;

import com.meridian.event.domain.model.valueobjects.Sku;

import java.time.Instant;

public class InventoryReservedEvent extends DomainEvent {
    private final String sku;
    private final int quantity;

    public InventoryReservedEvent(Sku sku, int quantity, String correlationId) {
        super(sku.value(), "INVENTORY_RESERVED", correlationId, null);
        this.sku = sku.value();
        this.quantity = quantity;
    }

    public String getSku() { return sku; }
    public int getQuantity() { return quantity; }
}
