package com.meridian.event.domain.model;

import com.meridian.event.domain.exception.DomainException;
import com.meridian.event.domain.model.valueobjects.Money;
import com.meridian.event.domain.model.valueobjects.OrderId;
import com.meridian.event.domain.model.valueobjects.Sku;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class Order {

    private final OrderId id;
    private String customerId;
    private Money total;
    private OrderStatus status;
    private final List<OrderLine> lines;
    private Instant createdAt;
    private Instant updatedAt;
    private long version;

    public Order(OrderId id, String customerId, List<OrderLine> lines) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.customerId = Objects.requireNonNull(customerId, "customerId cannot be null");
        this.lines = new ArrayList<>(Objects.requireNonNull(lines, "lines cannot be null"));
        this.total = Money.zero();
        this.status = OrderStatus.CREATED;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.version = 0L;
        recalculateTotal();
    }

    public void confirm() {
        if (status != OrderStatus.CREATED) {
            throw new DomainException("Cannot confirm order in status: " + status);
        }
        this.status = OrderStatus.CONFIRMED;
        this.updatedAt = Instant.now();
        this.version++;
    }

    public void cancel(String reason) {
        if (status == OrderStatus.CANCELLED || status == OrderStatus.COMPLETED) {
            throw new DomainException("Cannot cancel order in status: " + status);
        }
        this.status = OrderStatus.CANCELLED;
        this.updatedAt = Instant.now();
        this.version++;
    }

    public void addLine(Sku sku, int quantity, Money unitPrice) {
        if (status != OrderStatus.CREATED) {
            throw new DomainException("Cannot modify order in status: " + status);
        }
        OrderLine line = new OrderLine(sku, quantity, unitPrice);
        this.lines.add(line);
        recalculateTotal();
        this.updatedAt = Instant.now();
        this.version++;
    }

    private void recalculateTotal() {
        this.total = lines.stream()
                .map(OrderLine::subtotal)
                .reduce(Money.zero(), Money::add);
    }

    public OrderId getId() { return id; }
    public String getCustomerId() { return customerId; }
    public Money getTotal() { return total; }
    public OrderStatus getStatus() { return status; }
    public List<OrderLine> getLines() { return Collections.unmodifiableList(lines); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
public long getVersion() { return version; }

    // Package-private setters for persistence reconstruction
    public void setTotal(Money total) { this.total = total; }
    public void setStatus(OrderStatus status) { this.status = status; }
    public void setVersion(long version) { this.version = version; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

