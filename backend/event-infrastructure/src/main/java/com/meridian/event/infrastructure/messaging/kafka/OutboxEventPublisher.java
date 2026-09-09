package com.meridian.event.infrastructure.messaging.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridian.event.infrastructure.observability.BusinessMetrics;
import com.meridian.event.infrastructure.observability.CorrelationIdContext;
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

    private static final Logger log = LoggerFactory.getLogger("EVENT_PROCESSING");
    private static final int BATCH_SIZE = 100;
    private static final int MAX_RETRIES = 5;
    private static final String DLQ_TOPIC = "dlq.outbox.events";

    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final BusinessMetrics businessMetrics;

    public OutboxEventPublisher(OutboxEventRepository outboxRepository,
                                 KafkaTemplate<String, String> kafkaTemplate,
                                 ObjectMapper objectMapper,
                                 BusinessMetrics businessMetrics) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.businessMetrics = businessMetrics;
    }

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEventEntity> events = outboxRepository.findUnsentEvents(PageRequest.of(0, BATCH_SIZE));
        if (events.isEmpty()) {
            return;
        }

        for (OutboxEventEntity event : events) {
            String correlationId = extractCorrelationId(event.getPayload());
            if (correlationId != null) {
                CorrelationIdContext.setCorrelationId(correlationId);
            }

            try {
                String topic = resolveTopic(event.getEventType());
                kafkaTemplate.send(topic, event.getAggregateId(), event.getPayload()).get();

                event.setSentAt(Instant.now());
                event.setLastError(null);
                outboxRepository.save(event);

                log.info("Outbox event published eventId={} topic={} aggregateId={} correlationId={}",
                        event.getId(), topic, event.getAggregateId(), correlationId);

                businessMetrics.incrementOutboxEventsPublished();
            } catch (Exception e) {
                event.setRetryCount(event.getRetryCount() + 1);
                event.setLastError(e.getMessage());
                
                if (event.getRetryCount() >= MAX_RETRIES) {
                    sendToDeadLetterQueue(event, e);
                    event.setSentAt(Instant.now());
                    log.error("Outbox event max retries exceeded eventId={} topic={} error={}", 
                            event.getId(), topic, e.getMessage());
                    
                    businessMetrics.incrementOutboxEventsDlq();
                } else {
                    outboxRepository.save(event);
                    log.warn("Outbox event publish failed eventId={} attempt={}/{} error={}",
                            event.getId(), event.getRetryCount(), MAX_RETRIES, e.getMessage());
                    
                    businessMetrics.incrementOutboxEventsFailed();
                }
            } finally {
                CorrelationIdContext.clear();
            }
        }
    }

    private String extractCorrelationId(String payload) {
        try {
            com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(payload);
            if (node.has("correlationId") && !node.get("correlationId").isNull()) {
                return node.get("correlationId").asText();
            }
        } catch (Exception e) {
            // Ignore - correlationId not available
        }
        return null;
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
            log.info("Outbox event sent to DLQ eventId={} topic={}", event.getId(), DLQ_TOPIC);
        } catch (Exception dlqEx) {
            log.error("Failed to send outbox event to DLQ eventId={}", event.getId(), dlqEx);
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