package com.meridian.event.infrastructure.messaging.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridian.event.domain.model.DomainEvent;
import com.meridian.event.domain.model.OrderConfirmedEvent;
import com.meridian.event.infrastructure.persistence.jpa.ProcessedEventEntity;
import com.meridian.event.infrastructure.persistence.repository.ProcessedEventRepository;
import com.meridian.event.infrastructure.projection.OrderProjectionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@Import({KafkaTestConfig.class, ConsumerTestConfig.class})
@DirtiesContext
@EmbeddedKafka(partitions = 1, topics = {"order.events", "dlq.order.events"})
class OrderEventConsumerIntegrationTest {

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
    void shouldProcessEventAndCreateProjection() throws Exception {
        OrderConfirmedEvent event = new OrderConfirmedEvent(
                "order-123",
                "customer-456",
                "100.00",
                "corr-123"
        );
        String payload = objectMapper.writeValueAsString(event);

        // Send event to Kafka
        kafkaTemplate.send("order.events", "order-123", payload).get(5, TimeUnit.SECONDS);

        // Wait for processing
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            var projection = projectionHandler.getProjection("order-123");
            assertThat(projection).isPresent();
            assertThat(projection.get().getCustomerId()).isEqualTo("customer-456");
            assertThat(projection.get().getTotal().toString()).isEqualTo("100.00");
        });
    }

    @Test
    void shouldSkipDuplicateEvent() throws Exception {
        OrderConfirmedEvent event = new OrderConfirmedEvent(
                "order-dup-1",
                "customer-456",
                "100.00",
                "corr-123"
        );
        String payload = objectMapper.writeValueAsString(event);

        // Send same event twice
        kafkaTemplate.send("order.events", "order-dup-1", payload).get(5, TimeUnit.SECONDS);
        kafkaTemplate.send("order.events", "order-dup-1", payload).get(5, TimeUnit.SECONDS);

        // Wait for processing
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            long count = processedEventRepository.count();
            assertThat(count).isEqualTo(1); // Only one processed event recorded
        });
    }

    @Test
    void shouldSendToDLQAfterMaxRetries() throws Exception {
        // Send malformed event that will fail deserialization
        String badPayload = "{\"eventType\":\"UNKNOWN_TYPE\",\"eventId\":\"bad-1\"}";
        kafkaTemplate.send("order.events", "bad-1", badPayload).get(5, TimeUnit.SECONDS);

        // Wait for retries and DLQ
        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
            // Check DLQ has the message
            var records = com.meridian.event.infrastructure.resilience.KafkaTestHelper.getRecords(
                    consumerFactory, "dlq.order.events", 5000, 1
            );
            assertThat(records).isNotEmpty();
        });
    }
}
