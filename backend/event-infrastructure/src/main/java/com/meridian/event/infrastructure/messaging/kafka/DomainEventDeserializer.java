package com.meridian.event.infrastructure.messaging.kafka;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.meridian.event.domain.model.DomainEvent;
import com.meridian.event.domain.model.OrderConfirmedEvent;
import com.meridian.event.domain.model.PaymentProcessedEvent;
import com.meridian.event.domain.model.InventoryReservedEvent;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

public class DomainEventDeserializer extends StdDeserializer<DomainEvent> {

    public DomainEventDeserializer() {
        super(DomainEvent.class);
    }

    @Override
    public DomainEvent deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonNode node = parser.getCodec().readTree(parser);
        String eventType = node.get("eventType").asText();
        String eventId = node.get("eventId").asText();
        String aggregateId = node.get("aggregateId").asText();
        String correlationId = node.get("correlationId").asText();
        String causationId = node.has("causationId") && !node.get("causationId").isNull() 
                ? node.get("causationId").asText() : null;
        Instant occurredAt = Instant.parse(node.get("occurredAt").asText());

        DomainEvent event = switch (eventType) {
            case "ORDER_CONFIRMED" -> new OrderConfirmedEvent(
                    aggregateId,
                    node.get("customerId").asText(),
                    node.get("totalAmount").asText(),
                    correlationId
            );
            case "PAYMENT_PROCESSED" -> new PaymentProcessedEvent(
                    aggregateId,
                    node.get("orderId").asText(),
                    node.get("amount").asText(),
                    node.get("status").asText(),
                    correlationId
            );
            case "INVENTORY_RESERVED" -> new InventoryReservedEvent(
                    com.meridian.event.domain.model.valueobjects.Sku.of(node.get("sku").asText()),
                    node.get("quantity").asInt(),
                    correlationId
            );
            default -> throw new IllegalArgumentException("Unknown event type: " + eventType);
        };

        // Set the original eventId and occurredAt for idempotency
        event.setEventId(eventId);
        event.setOccurredAt(occurredAt);
        return event;
    }
}