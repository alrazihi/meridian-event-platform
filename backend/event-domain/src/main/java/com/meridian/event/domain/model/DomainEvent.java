package com.meridian.event.domain.model;

import java.time.Instant;
import java.util.UUID;

public abstract class DomainEvent {
    private String eventId;
    private final String aggregateId;
    private final String eventType;
    private Instant occurredAt;
    private final String correlationId;
    private final String causationId;

    protected DomainEvent(String aggregateId, String eventType, String correlationId, String causationId) {
        this.eventId = UUID.randomUUID().toString();
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.occurredAt = Instant.now();
        this.correlationId = correlationId;
        this.causationId = causationId;
    }

    protected DomainEvent(String eventId, String aggregateId, String eventType, Instant occurredAt, String correlationId, String causationId) {
        this.eventId = eventId;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.occurredAt = occurredAt;
        this.correlationId = correlationId;
        this.causationId = causationId;
    }

    public String getEventId() { return eventId; }
    public String getAggregateId() { return aggregateId; }
    public String getEventType() { return eventType; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getCorrelationId() { return correlationId; }
    public String getCausationId() { return causationId; }

    // Package-private setters for deserialization
    public void setEventId(String eventId) { this.eventId = eventId; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
}
