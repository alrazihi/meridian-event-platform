package com.meridian.event.infrastructure.messaging.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridian.event.infrastructure.persistence.jpa.ProcessedEventEntity;
import com.meridian.event.infrastructure.persistence.repository.ProcessedEventRepository;
import com.meridian.event.infrastructure.projection.OrderProjectionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class ConsumerTestConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.databind.module.SimpleModule()
                .addDeserializer(com.meridian.event.domain.model.DomainEvent.class, new DomainEventDeserializer()));
        return mapper;
    }

    @Bean
    public OrderEventConsumer orderEventConsumer(
            ObjectMapper objectMapper,
            ProcessedEventRepository processedEventRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            OrderProjectionHandler projectionHandler,
            org.springframework.transaction.support.TransactionTemplate transactionTemplate) {
        return new OrderEventConsumer(
                objectMapper,
                processedEventRepository,
                kafkaTemplate,
                projectionHandler,
                transactionTemplate
        );
    }

    @Bean
    public OrderProjectionHandler orderProjectionHandler(
            com.meridian.event.infrastructure.persistence.repository.OrderProjectionRepository projectionRepository) {
        return new OrderProjectionHandler(projectionRepository);
    }
}