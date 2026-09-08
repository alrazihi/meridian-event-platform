package com.meridian.event.infrastructure.messaging.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridian.event.domain.model.DomainEvent;
import com.meridian.event.infrastructure.persistence.jpa.ProcessedEventEntity;
import com.meridian.event.infrastructure.persistence.repository.ProcessedEventRepository;
import com.meridian.event.infrastructure.projection.OrderProjectionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Component
public class OrderEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);
    private static final String DLQ_TOPIC = "dlq.order.events";
    private static final int MAX_RETRIES = 3;

    private final ObjectMapper objectMapper;
    private final ProcessedEventRepository processedEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OrderProjectionHandler projectionHandler;

    public OrderEventConsumer(ObjectMapper objectMapper,
                               ProcessedEventRepository processedEventRepository,
                               KafkaTemplate<String, String> kafkaTemplate,
                               OrderProjectionHandler projectionHandler) {
        this.objectMapper = objectMapper;
        this.processedEventRepository = processedEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.projectionHandler = projectionHandler;
        
        objectMapper.registerModule(new com.fasterxml.jackson.databind.module.SimpleModule()
                .addDeserializer(DomainEvent.class, new DomainEventDeserializer()));
    }

    @KafkaListener(topics = "order.events", groupId = "order-projection")
    @Retryable(
        retryFor = Exception.class,
        maxAttempts = MAX_RETRIES,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    @Transactional
    public void consumeOrderEvent(
            @Payload String payload,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = KafkaHeaders.RECEIVED_TIMESTAMP, required = false) Long timestamp,
            Acknowledgment acknowledgment) {
        
        DomainEvent event = objectMapper.readValue(payload, DomainEvent.class);
        String eventId = event.getEventId();

        if (processedEventRepository.existsByEventId(eventId)) {
            log.debug("Duplicate event {} skipped", eventId);
            acknowledgment.acknowledge();
            return;
        }

        try {
            projectionHandler.handle(event);
            
            ProcessedEventEntity processed = new ProcessedEventEntity();
            processed.setEventId(eventId);
            processed.setAggregateId(event.getAggregateId());
            processed.setEventType(event.getEventType());
            processed.setProcessedAt(Instant.now());
            processedEventRepository.save(processed);
            
            log.info("Processed event {} for order {} at offset {}", event.getEventType(), event.getAggregateId(), offset);
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process event {} for key {} at offset {}: {}", 
                    eventId, key, offset, e.getMessage());
            throw e;
        }
    }

    @Recover
    @Transactional
    public void recover(Exception ex, String payload, String key, long offset, Long timestamp, Acknowledgment acknowledgment) {
        log.error("Max retries exhausted for event at offset {}, sending to DLQ", offset, ex);
        
        try {
            DomainEvent event = objectMapper.readValue(payload, DomainEvent.class);
            
            String dlqPayload = String.format(
                "{\"originalPayload\":%s,\"error\":\"%s\",\"failedAt\":\"%s\",\"retryCount\":%d}",
                payload, ex.getMessage(), Instant.now(), MAX_RETRIES
            );
            
            kafkaTemplate.send(DLQ_TOPIC, key, dlqPayload).get();
            log.info("Sent failed event to DLQ topic: {}", DLQ_TOPIC);
        } catch (Exception dlqEx) {
            log.error("Failed to send event to DLQ", dlqEx);
        }
        
        acknowledgment.acknowledge();
    }
}