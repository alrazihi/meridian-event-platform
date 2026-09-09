package com.meridian.event.infrastructure.resilience;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridian.event.domain.model.DomainEvent;
import com.meridian.event.domain.model.OrderConfirmedEvent;
import com.meridian.event.infrastructure.messaging.kafka.OrderEventConsumer;
import com.meridian.event.infrastructure.persistence.jpa.ProcessedEventEntity;
import com.meridian.event.infrastructure.persistence.repository.ProcessedEventRepository;
import com.meridian.event.infrastructure.projection.OrderProjectionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@Import(ResilienceTestConfig.class)
@DirtiesContext
class MalformedEventTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ConsumerFactory<String, String> consumerFactory;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private OrderProjectionHandler projectionHandler;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderEventConsumer consumer;

    @BeforeEach
    void setUp() {
        processedEventRepository.deleteAll();
    }

    @Test
    void shouldHandleInvalidJSONGracefully() throws Exception {
        // When: Send completely invalid JSON
        String invalidJson = "not valid json at all {{{";
        kafkaTemplate.send("order.events", "bad-json-1", invalidJson).get(5, TimeUnit.SECONDS);

        // Then: Should go to DLQ after retries, not crash consumer
        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
            var records = com.meridian.event.infrastructure.resilience.KafkaTestHelper.getRecords(
                    consumerFactory, "dlq.order.events", 5000, 1
            );
            assertThat(records).isNotEmpty();
        });
    }

    @Test
    void shouldHandleMissingRequiredFields() throws Exception {
        // When: Send event missing required fields
        String incompleteEvent = "{\"eventType\":\"ORDER_CONFIRMED\"}"; // Missing eventId, aggregateId, etc.
        kafkaTemplate.send("order.events", "incomplete-1", incompleteEvent).get(5, TimeUnit.SECONDS);

        // Then: Should go to DLQ
        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
            var records = com.meridian.event.infrastructure.resilience.KafkaTestHelper.getRecords(
                    consumerFactory, "dlq.order.events", 5000, 1
            );
            assertThat(records).isNotEmpty();
        });
    }

    @Test
    void shouldHandleUnknownEventType() throws Exception {
        // When: Send event with unknown type
        String unknownEvent = "{\"eventType\":\"UNKNOWN_TYPE\",\"eventId\":\"unknown-1\",\"aggregateId\":\"order-1\",\"customerId\":\"cust-1\",\"occurredAt\":\"2024-01-01T00:00:00Z\"}";
        kafkaTemplate.send("order.events", "unknown-1", unknownEvent).get(5, TimeUnit.SECONDS);

        // Then: Should go to DLQ (deserializer may fail or handler ignores)
        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
            var records = com.meridian.event.infrastructure.resilience.KafkaTestHelper.getRecords(
                    consumerFactory, "dlq.order.events", 5000, 1
            );
            assertThat(records).isNotEmpty();
        });
    }

    @Test
    void shouldNotBlockPartitionOnPoisonPill() throws Exception {
        // When: Send poison pill (always fails)
        String poisonPill = "{\"eventType\":\"POISON\",\"eventId\":\"poison-1\"}";
        kafkaTemplate.send("order.events", "poison-1", poisonPill).get(5, TimeUnit.SECONDS);

        // Then: After retries exhausted, goes to DLQ and consumer continues
        await().atMost(25, TimeUnit.SECONDS).untilAsserted(() -> {
            var records = com.meridian.event.infrastructure.resilience.KafkaTestHelper.getRecords(
                    consumerFactory, "dlq.order.events", 5000, 1
            );
            assertThat(records).isNotEmpty();
        });

        // And: Consumer should still process valid events after
        String orderId = "order-after-poison";
        String customerId = "customer-after-poison";
        OrderConfirmedEvent validEvent = new OrderConfirmedEvent(
                orderId, customerId, "100.00", UUID.randomUUID().toString()
        );
        String validPayload = objectMapper.writeValueAsString(validEvent);
        
        kafkaTemplate.send("order.events", orderId, validPayload).get(5, TimeUnit.SECONDS);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(processedEventRepository.count()).isEqualTo(1);
            var projection = projectionHandler.getProjection(orderId);
            assertThat(projection).isPresent();
        });
    }

    @Test
    void shouldHandleNullPayload() throws Exception {
        // When: Send null payload
        kafkaTemplate.send("order.events", "null-payload", (String) null).get(5, TimeUnit.SECONDS);

        // Then: Should handle gracefully (go to DLQ)
        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
            var records = com.meridian.event.infrastructure.resilience.KafkaTestHelper.getRecords(
                    consumerFactory, "dlq.order.events", 5000, 1
            );
            assertThat(records).isNotEmpty();
        });
    }
}
