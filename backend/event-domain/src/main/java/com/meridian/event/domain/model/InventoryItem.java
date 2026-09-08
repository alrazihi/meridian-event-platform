package com.meridian.event.domain.model;

import com.meridian.event.domain.model.valueobjects.Sku;

import java.time.Instant;
import java.util.Objects;

public class InventoryItem {
    private final Sku sku;
    private int availableQuantity;
    private int reservedQuantity;
    private final Instant createdAt;
    private Instant updatedAt;
    private long version;

    public InventoryItem(Sku sku, int availableQuantity) {
        this.sku = Objects.requireNonNull(sku, "sku cannot be null");
        this.availableQuantity = availableQuantity;
        this.reservedQuantity = 0;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.version = 0L;
    }

    public void reserve(int quantity) {
        if (quantity <= 0) {
            throw new DomainException("Reserve quantity must be positive");
        }
        if (availableQuantity < quantity) {
            throw new DomainException("Insufficient inventory for SKU: " + sku.value());
        }
        this.availableQuantity -= quantity;
        this.reservedQuantity += quantity;
        this.updatedAt = Instant.now();
        this.version++;
    }

    public void release(int quantity) {
        if (quantity <= 0) {
            throw new DomainException("Release quantity must be positive");
        }
        if (reservedQuantity < quantity) {
            throw new DomainException("Cannot release more than reserved for SKU: " + sku.value());
        }
        this.reservedQuantity -= quantity;
        this.availableQuantity += quantity;
        this.updatedAt = Instant.now();
        this.version++;
    }

    public Sku getSku() { return sku; }
    public int getAvailableQuantity() { return availableQuantity; }
    public int getReservedQuantity() { return reservedQuantity; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
