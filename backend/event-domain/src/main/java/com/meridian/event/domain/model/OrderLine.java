package com.meridian.event.domain.model;

import com.meridian.event.domain.exception.DomainException;
import com.meridian.event.domain.model.valueobjects.Money;
import com.meridian.event.domain.model.valueobjects.Sku;

import java.util.Objects;

public class OrderLine {
    private final Sku sku;
    private final int quantity;
    private final Money unitPrice;

    public OrderLine(Sku sku, int quantity, Money unitPrice) {
        this.sku = Objects.requireNonNull(sku, "sku cannot be null");
        this.quantity = quantity;
        this.unitPrice = Objects.requireNonNull(unitPrice, "unitPrice cannot be null");
        if (quantity <= 0) {
            throw new DomainException("Quantity must be positive");
        }
    }

    public Money subtotal() {
        return unitPrice.multiply(quantity);
    }

    public Sku getSku() { return sku; }
    public int getQuantity() { return quantity; }
    public Money getUnitPrice() { return unitPrice; }
}

