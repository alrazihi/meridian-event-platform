package com.meridian.event.domain.model;

import com.meridian.event.domain.model.valueobjects.OrderId;

import java.time.Instant;

public class OrderConfirmedEvent extends DomainEvent {
    private final String customerId;
    private final String totalAmount;

    public OrderConfirmedEvent(String aggregateId, String customerId, String totalAmount, String correlationId) {
        super(aggregateId, "ORDER_CONFIRMED", correlationId, null);
        this.customerId = customerId;
        this.totalAmount = totalAmount;
    }

    public String getCustomerId() { return customerId; }
    public String getTotalAmount() { return totalAmount; }
}
