package com.meridian.event.infrastructure.messaging.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridian.event.domain.model.DomainEvent;
import com.meridian.event.infrastructure.persistence.jpa.OutboxEventEntity;
import com.meridian.event.infrastructure.persistence.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class KafkaEventPublisher implements com.meridian.event.application.port.outbound.EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventPublisher.class);
    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public KafkaEventPublisher(OutboxEventRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void publish(DomainEvent event) {
        try {
            String key = event.getAggregateId();
            String value = objectMapper.writeValueAsString(event);
            
            OutboxEventEntity outboxEvent = new OutboxEventEntity();
            outboxEvent.setId(UUID.randomUUID().toString());
            outboxEvent.setAggregateId(key);
            outboxEvent.setEventType(event.getEventType());
            outboxEvent.setPayload(value);
            
            outboxRepository.save(outboxEvent);
            
            log.debug("Persisted event {} for aggregate {} to outbox", event.getEventType(), key);
        } catch (Exception e) {
            log.error("Failed to persist event to outbox for aggregate {}", event.getAggregateId(), e);
            throw new RuntimeException("Failed to persist event to outbox", e);
        }
    }
}
