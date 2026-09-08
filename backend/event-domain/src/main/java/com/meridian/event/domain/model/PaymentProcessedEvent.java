package com.meridian.event.domain.model;

import com.meridian.event.domain.model.valueobjects.PaymentId;

import java.time.Instant;

public class PaymentProcessedEvent extends DomainEvent {
    private final String orderId;
    private final String amount;
    private final String status;

    public PaymentProcessedEvent(String aggregateId, String orderId, String amount, String status, String correlationId) {
        super(aggregateId, "PAYMENT_PROCESSED", correlationId, null);
        this.orderId = orderId;
        this.amount = amount;
        this.status = status;
    }

    public String getOrderId() { return orderId; }
    public String getAmount() { return amount; }
    public String getStatus() { return status; }
}
