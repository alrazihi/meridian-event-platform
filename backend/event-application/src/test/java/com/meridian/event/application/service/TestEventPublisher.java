package com.meridian.event.application.service;

import com.meridian.event.application.port.outbound.EventPublisher;
import com.meridian.event.domain.model.DomainEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
class TestEventPublisher implements EventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    TestEventPublisher(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void publish(DomainEvent event) {
        try {
            String key = event.getAggregateId();
            String value = objectMapper.writeValueAsString(event);
            String topic = resolveTopic(event.getEventType());
            kafkaTemplate.send(topic, key, value).get();
        } catch (Exception e) {
            throw new RuntimeException("Failed to publish event", e);
        }
    }

    private String resolveTopic(String eventType) {
        return switch (eventType) {
            case "ORDER_CONFIRMED" -> "order.events";
            case "PAYMENT_PROCESSED" -> "payment.events";
            case "INVENTORY_RESERVED" -> "inventory.events";
            default -> "order.events";
        };
    }
}