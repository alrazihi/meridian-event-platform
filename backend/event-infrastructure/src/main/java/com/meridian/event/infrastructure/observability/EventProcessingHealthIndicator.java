package com.meridian.event.infrastructure.observability;

import com.meridian.event.infrastructure.persistence.repository.ProcessedEventRepository;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

@Component
public class EventProcessingHealthIndicator implements HealthIndicator {

    private static final int MAX_UNPROCESSED_EVENTS = 100;
    private static final int MAX_AGE_MINUTES = 10;

    private final ProcessedEventRepository processedEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public EventProcessingHealthIndicator(ProcessedEventRepository processedEventRepository,
                                           KafkaTemplate<String, String> kafkaTemplate) {
        this.processedEventRepository = processedEventRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public Health health() {
        long totalProcessed = processedEventRepository.count();
        long recentProcessed = processedEventRepository.countRecentEvents(Instant.now().minus(MAX_AGE_MINUTES, ChronoUnit.MINUTES));
        
        // Check Kafka connectivity
        boolean kafkaHealthy = checkKafkaConnectivity();

        Health.Builder builder = kafkaHealthy ? Health.up() : Health.down();
        
        builder.withDetail("totalProcessedEvents", totalProcessed)
                .withDetail("recentProcessedEvents", recentProcessed)
                .withDetail("kafkaConnectivity", kafkaHealthy ? "UP" : "DOWN")
                .withDetail("maxUnprocessedThreshold", MAX_UNPROCESSED_EVENTS);

        if (!kafkaHealthy) {
            builder.withDetail("error", "Kafka broker unavailable");
        }

        return builder.build();
    }

    private boolean checkKafkaConnectivity() {
        try {
            String defaultTopic = kafkaTemplate.getDefaultTopic();
            if (defaultTopic == null || defaultTopic.isEmpty()) {
                kafkaTemplate.execute(operations -> operations.partitionsFor("order.events"));
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}