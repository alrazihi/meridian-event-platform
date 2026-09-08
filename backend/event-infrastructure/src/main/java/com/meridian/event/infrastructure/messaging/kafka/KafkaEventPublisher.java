package com.meridian.event.infrastructure.messaging.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridian.event.domain.model.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class KafkaEventPublisher implements com.meridian.event.application.port.outbound.EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventPublisher.class);
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public KafkaEventPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(DomainEvent event) {
        try {
            String key = event.getAggregateId();
            String value = objectMapper.writeValueAsString(event);
            kafkaTemplate.send("order.events", key, value);
            log.info("Published event {} for aggregate {}", event.getEventType(), event.getAggregateId());
        } catch (Exception e) {
            log.error("Failed to publish event for aggregate {}", event.getAggregateId(), e);
            throw new RuntimeException("Failed to publish event", e);
        }
    }
}
