package com.meridian.event.domain.model;

import com.meridian.event.domain.exception.DomainException;
import com.meridian.event.domain.model.valueobjects.Money;
import com.meridian.event.domain.model.valueobjects.PaymentId;

import java.time.Instant;
import java.util.Objects;

public class Payment {
    private final PaymentId id;
    private String orderId;
    private Money amount;
    private PaymentStatus status;
    private String paymentMethod;
    private String transactionId;
    private final Instant createdAt;
    private Instant updatedAt;
    private long version;

    public Payment(PaymentId id, String orderId, Money amount, String paymentMethod) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.orderId = Objects.requireNonNull(orderId, "orderId cannot be null");
        this.amount = Objects.requireNonNull(amount, "amount cannot be null");
        this.paymentMethod = Objects.requireNonNull(paymentMethod, "paymentMethod cannot be null");
        this.status = PaymentStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.version = 0L;
    }

    public void approve() {
        if (status != PaymentStatus.PENDING) {
            throw new DomainException("Cannot approve payment in status: " + status);
        }
        this.status = PaymentStatus.APPROVED;
        this.updatedAt = Instant.now();
        this.version++;
    }

    public void reject(String reason) {
        if (status != PaymentStatus.PENDING) {
            throw new DomainException("Cannot reject payment in status: " + status);
        }
        this.status = PaymentStatus.REJECTED;
        this.updatedAt = Instant.now();
        this.version++;
    }

    public PaymentId getId() { return id; }
    public String getOrderId() { return orderId; }
    public Money getAmount() { return amount; }
    public PaymentStatus getStatus() { return status; }
    public String getPaymentMethod() { return paymentMethod; }
    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }

    // Package-private setters for persistence reconstruction
    void setAmount(Money amount) { this.amount = amount; }
    void setStatus(PaymentStatus status) { this.status = status; }
    void setVersion(long version) { this.version = version; }
    void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

