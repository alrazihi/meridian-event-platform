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
    private static final int MAX_RETRIES = 5;
    private static final String DLQ_TOPIC = "dlq.outbox.events";

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
                
                if (event.getRetryCount() >= MAX_RETRIES) {
                    // Max retries exceeded - send to DLQ and mark as failed
                    sendToDeadLetterQueue(event, e);
                    event.setSentAt(Instant.now()); // Mark as "processed" (failed permanently)
                    log.error("Outbox event {} exceeded max retries, sent to DLQ", event.getId());
                }
                
                outboxRepository.save(event);
                log.warn("Failed to publish outbox event {} (attempt {}/{}): {}", 
                        event.getId(), event.getRetryCount(), MAX_RETRIES, e.getMessage());
            }
        }
    }

    private void sendToDeadLetterQueue(OutboxEventEntity event, Exception originalError) {
        try {
            String dlqPayload = String.format(
                    "{\"originalEventId\":\"%s\",\"aggregateId\":\"%s\",\"eventType\":\"%s\"," +
                    "\"originalPayload\":%s,\"error\":\"%s\",\"failedAt\":\"%s\",\"retryCount\":%d}",
                    event.getId(), event.getAggregateId(), event.getEventType(),
                    event.getPayload(), originalError.getMessage(), Instant.now(), MAX_RETRIES
            );
            kafkaTemplate.send(DLQ_TOPIC, event.getAggregateId(), dlqPayload).get();
            log.info("Sent failed outbox event {} to DLQ topic {}", event.getId(), DLQ_TOPIC);
        } catch (Exception dlqEx) {
            log.error("Failed to send outbox event {} to DLQ", event.getId(), dlqEx);
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