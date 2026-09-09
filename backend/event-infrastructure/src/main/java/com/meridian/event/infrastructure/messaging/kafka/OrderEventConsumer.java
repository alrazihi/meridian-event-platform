package com.meridian.event.infrastructure.messaging.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridian.event.domain.model.DomainEvent;
import com.meridian.event.domain.model.OrderConfirmedEvent;
import com.meridian.event.infrastructure.observability.BusinessMetrics;
import com.meridian.event.infrastructure.observability.CorrelationIdContext;
import com.meridian.event.infrastructure.observability.KafkaCorrelationIdConsumerInterceptor;
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
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;

@Component
public class OrderEventConsumer {

    private static final Logger log = LoggerFactory.getLogger("EVENT_PROCESSING");
    private static final String DLQ_TOPIC = "dlq.order.events";
    private static final int MAX_RETRIES = 3;

    private final ObjectMapper objectMapper;
    private final ProcessedEventRepository processedEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OrderProjectionHandler projectionHandler;
    private final TransactionTemplate transactionTemplate;
    private final BusinessMetrics businessMetrics;

    public OrderEventConsumer(ObjectMapper objectMapper,
                               ProcessedEventRepository processedEventRepository,
                               KafkaTemplate<String, String> kafkaTemplate,
                               OrderProjectionHandler projectionHandler,
                               TransactionTemplate transactionTemplate,
                               BusinessMetrics businessMetrics) {
        this.objectMapper = objectMapper;
        this.processedEventRepository = processedEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.projectionHandler = projectionHandler;
        this.transactionTemplate = transactionTemplate;
        this.businessMetrics = businessMetrics;
        
        objectMapper.registerModule(new com.fasterxml.jackson.databind.module.SimpleModule()
                .addDeserializer(DomainEvent.class, new DomainEventDeserializer()));
    }

    @KafkaListener(topics = "order.events", groupId = "order-projection")
    @Retryable(
        retryFor = Exception.class,
        maxAttempts = MAX_RETRIES,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public void consumeOrderEvent(
            @Payload String payload,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = KafkaHeaders.RECEIVED_TIMESTAMP, required = false) Long timestamp,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            Acknowledgment acknowledgment) {
        
        try {
            DomainEvent event = objectMapper.readValue(payload, DomainEvent.class);
            String eventId = event.getEventId();

            // Set correlation context from Kafka headers for structured logging
            CorrelationIdContext.setCorrelationId(event.getCorrelationId());
            CorrelationIdContext.setTraceId(event.getCorrelationId()); // Use correlationId as traceId if no separate traceId
            
            log.info("Received event eventId={} eventType={} aggregateId={} correlationId={} partition={} offset={}",
                    eventId, event.getEventType(), event.getAggregateId(), event.getCorrelationId(), partition, offset);

            // Extract customer ID for tenant isolation of idempotency keys
            String customerId = extractCustomerId(event);

            if (processedEventRepository.existsByEventIdAndCustomerId(eventId, customerId)) {
                log.info("Duplicate event skipped eventId={} customerId={}", eventId, customerId);
                acknowledgment.acknowledge();
                return;
            }

            transactionTemplate.execute(status -> {
                projectionHandler.handle(event);
                
                ProcessedEventEntity processed = new ProcessedEventEntity();
                processed.setEventId(eventId);
                processed.setAggregateId(event.getAggregateId());
                processed.setEventType(event.getEventType());
                processed.setCustomerId(customerId);
                processed.setProcessedAt(Instant.now());
                processedEventRepository.save(processed);
                
                log.info("Event processed eventId={} eventType={} aggregateId={} customerId={}", 
                        eventId, event.getEventType(), event.getAggregateId(), customerId);
                return null;
            });
            
            businessMetrics.incrementEventsProcessed();
            acknowledgment.acknowledge();
        } finally {
            CorrelationIdContext.clear();
        }
    }

    private String extractCustomerId(DomainEvent event) {
        if (event instanceof OrderConfirmedEvent orderEvent) {
            return orderEvent.getCustomerId();
        }
        // Fallback for other event types - use aggregate ID as customer ID
        // In a real system, all events should carry customer/tenant context
        return event.getAggregateId();
    }

    @Recover
    public void recover(Exception ex, String payload, String key, long offset, Long timestamp, Acknowledgment acknowledgment) {
        log.error("Max retries exhausted for event at offset {}, sending to DLQ error={}", offset, ex.getMessage(), ex);
        
        try {
            DomainEvent event = objectMapper.readValue(payload, DomainEvent.class);
            
            String dlqPayload = String.format(
                "{\"originalPayload\":%s,\"error\":\"%s\",\"failedAt\":\"%s\",\"retryCount\":%d}",
                payload, ex.getMessage(), Instant.now(), MAX_RETRIES
            );
            
            kafkaTemplate.send(DLQ_TOPIC, key, dlqPayload).get();
            log.info("Sent failed event to DLQ topic={} eventId={}", DLQ_TOPIC, event.getEventId());
            
            businessMetrics.incrementEventsFailed();
            businessMetrics.incrementEventsDlq();
        } catch (Exception dlqEx) {
            log.error("Failed to send event to DLQ", dlqEx);
        }
        
        acknowledgment.acknowledge();
    }
}