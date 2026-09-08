package com.meridian.event.infrastructure.resilience;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridian.event.domain.model.DomainEvent;
import com.meridian.event.domain.model.OrderConfirmedEvent;
import com.meridian.event.infrastructure.messaging.kafka.KafkaEventPublisher;
import com.meridian.event.infrastructure.messaging.kafka.OrderEventConsumer;
import com.meridian.event.infrastructure.messaging.kafka.OutboxEventPublisher;
import com.meridian.event.infrastructure.persistence.jpa.OutboxEventEntity;
import com.meridian.event.infrastructure.persistence.jpa.ProcessedEventEntity;
import com.meridian.event.infrastructure.persistence.repository.OutboxEventRepository;
import com.meridian.event.infrastructure.persistence.repository.ProcessedEventRepository;
import com.meridian.event.infrastructure.projection.OrderProjectionHandler;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Testcontainers
@Configuration
class ResilienceTestConfig {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("meridian")
            .withUsername("meridian")
            .withPassword("meridian");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer("confluentinc/cp-kafka:7.5.0");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    }

    @Bean
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        return new KafkaAdmin(configs);
    }

    @Bean
    @Primary
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        configs.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configs.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configs.put(ProducerConfig.ACKS_CONFIG, "all");
        configs.put(ProducerConfig.RETRIES_CONFIG, 3);
        configs.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        return new DefaultKafkaProducerFactory<>(configs);
    }

    @Bean
    @Primary
    public KafkaTemplate<String, String> kafkaTemplate(ProducerFactory<String, String> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        configs.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group-resilience");
        configs.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        configs.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configs.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configs.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        configs.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
        return new DefaultKafkaConsumerFactory<>(configs);
    }

    @Bean
    public NewTopic orderEventsTopic() {
        return TopicBuilder.name("order.events").partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic paymentEventsTopic() {
        return TopicBuilder.name("payment.events").partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic inventoryEventsTopic() {
        return TopicBuilder.name("inventory.events").partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic dlqOrderEventsTopic() {
        return TopicBuilder.name("dlq.order.events").partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic dlqOutboxEventsTopic() {
        return TopicBuilder.name("dlq.outbox.events").partitions(1).replicas(1).build();
    }

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.databind.module.SimpleModule()
                .addDeserializer(DomainEvent.class, new com.meridian.event.infrastructure.messaging.kafka.DomainEventDeserializer()));
        return mapper;
    }

    @Bean
    public OrderProjectionHandler orderProjectionHandler(
            com.meridian.event.infrastructure.persistence.repository.OrderProjectionRepository projectionRepository) {
        return new OrderProjectionHandler(projectionRepository);
    }

    @Bean
    public TransactionTemplate transactionTemplate(org.springframework.transaction.PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }

    @Bean
    public OrderEventConsumer orderEventConsumer(
            ObjectMapper objectMapper,
            ProcessedEventRepository processedEventRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            OrderProjectionHandler projectionHandler,
            TransactionTemplate transactionTemplate) {
        return new OrderEventConsumer(
                objectMapper,
                processedEventRepository,
                kafkaTemplate,
                projectionHandler,
                transactionTemplate
        );
    }

    @Bean
    public OutboxEventPublisher outboxEventPublisher(
            OutboxEventRepository outboxRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper) {
        return new OutboxEventPublisher(outboxRepository, kafkaTemplate, objectMapper);
    }

    @Bean
    public KafkaEventPublisher kafkaEventPublisher(
            OutboxEventRepository outboxRepository,
            ObjectMapper objectMapper) {
        return new KafkaEventPublisher(outboxRepository, objectMapper);
    }
}