package com.meridian.event.infrastructure.messaging.kafka;

import com.meridian.event.infrastructure.observability.BusinessMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridian.event.infrastructure.persistence.jpa.OutboxEventEntity;
import com.meridian.event.infrastructure.persistence.repository.OutboxEventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
@EnableScheduling
class OutboxTestConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public BusinessMetrics businessMetrics() {
        return new BusinessMetrics(new SimpleMeterRegistry());
    }

    @Bean
    public TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }

    @Bean
    public OutboxEventPublisher outboxEventPublisher(
            OutboxEventRepository outboxRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            BusinessMetrics businessMetrics) {
        return new OutboxEventPublisher(outboxRepository, kafkaTemplate, objectMapper, businessMetrics);
    }
}