package com.meridian.event.infrastructure.messaging.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridian.event.infrastructure.persistence.jpa.OutboxEventEntity;
import com.meridian.event.infrastructure.persistence.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
public class OutboxEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventPublisher.class);
    private static final int BATCH_SIZE = 100;

    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public OutboxEventPublisher(OutboxEventRepository outboxRepository,
                                 KafkaTemplate<String, String> kafkaTemplate,
                                 ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEventEntity> events = outboxRepository.findUnsentEvents(PageRequest.of(0, BATCH_SIZE));
        if (events.isEmpty()) {
            return;
        }

        for (OutboxEventEntity event : events) {
            try {
                String topic = resolveTopic(event.getEventType());
                kafkaTemplate.send(topic, event.getAggregateId(), event.getPayload()).get();
                
                event.setSentAt(Instant.now());
                event.setLastError(null);
                outboxRepository.save(event);
                
                log.debug("Published outbox event {} to topic {}", event.getId(), topic);
            } catch (Exception e) {
                event.setRetryCount(event.getRetryCount() + 1);
                event.setLastError(e.getMessage());
                outboxRepository.save(event);
                log.warn("Failed to publish outbox event {} (attempt {}/5): {}", 
                        event.getId(), event.getRetryCount(), e.getMessage());
            }
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