package com.meridian.event.infrastructure.messaging.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridian.event.domain.model.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
public class OrderEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);
    private final ObjectMapper objectMapper;

    public OrderEventConsumer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "order.events", groupId = "order-projection")
    public void consumeOrderEvent(
            @Payload String payload,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            @Header(KafkaHeaders.OFFSET) long offset) {
        try {
            DomainEvent event = objectMapper.readValue(payload, DomainEvent.class);
            log.info("Consumed event {} for order {} at offset {}", event.getEventType(), event.getAggregateId(), offset);
        } catch (Exception e) {
            log.error("Failed to deserialize event for key {} at offset {}", key, offset, e);
        }
    }
}
