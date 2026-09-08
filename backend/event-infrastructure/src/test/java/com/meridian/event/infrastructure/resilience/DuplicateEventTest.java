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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@Import(ResilienceTestConfig.class)
@DirtiesContext
class DuplicateEventTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private OrderProjectionHandler projectionHandler;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderEventConsumer consumer;

    private static final String ORDER_ID = "order-duplicate-test";
    private static final String CUSTOMER_ID = "customer-duplicate";

    @BeforeEach
    void setUp() {
        processedEventRepository.deleteAll();
    }

    @Test
    void shouldProcessEventExactlyOnceDespiteRedelivery() throws InterruptedException {
        // Given: An event
        OrderConfirmedEvent event = new OrderConfirmedEvent(
                ORDER_ID, CUSTOMER_ID, "250.00", "event-duplicate-1"
        );
        String payload = objectMapper.writeValueAsString(event);

        // When: Send same event multiple times (simulating Kafka redelivery)
        int redeliveryCount = 5;
        for (int i = 0; i < redeliveryCount; i++) {
            kafkaTemplate.send("order.events", ORDER_ID, payload).get(5, TimeUnit.SECONDS);
        }

        // Then: Exactly one processed event recorded
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            long count = processedEventRepository.count();
            assertThat(count).isEqualTo(1);
        });

        // And: Projection created once
        var projection = projectionHandler.getProjection(ORDER_ID);
        assertThat(projection).isPresent();
        assertThat(projection.get().getVersion()).isEqualTo(1L);
    }

    @Test
    void shouldHandleConcurrentDuplicateDeliveries() throws InterruptedException {
        // Given: An event
        OrderConfirmedEvent event = new OrderConfirmedEvent(
                ORDER_ID + "-concurrent", CUSTOMER_ID, "100.00", "event-concurrent-dup"
        );
        String payload = objectMapper.writeValueAsString(event);

        // When: Multiple threads send duplicates simultaneously
        int threadCount = 10;
        int deliveriesPerThread = 3;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < deliveriesPerThread; j++) {
                        kafkaTemplate.send("order.events", ORDER_ID + "-concurrent", payload).get(5, TimeUnit.SECONDS);
                    }
                } catch (Exception e) {
                    // Ignore
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertThat(endLatch.await(30, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        // Then: Exactly one processed event
        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            long count = processedEventRepository.count();
            assertThat(count).isEqualTo(1);
        });
    }

    @Test
    void shouldIsolateIdempotencyByCustomer() throws Exception {
        // Given: Same event ID for different customers
        String sharedEventId = "shared-event-id";
        
        OrderConfirmedEvent event1 = new OrderConfirmedEvent(
                ORDER_ID + "-1", CUSTOMER_ID + "-1", "100.00", sharedEventId
        );
        OrderConfirmedEvent event2 = new OrderConfirmedEvent(
                ORDER_ID + "-2", CUSTOMER_ID + "-2", "200.00", sharedEventId
        );

        String payload1 = objectMapper.writeValueAsString(event1);
        String payload2 = objectMapper.writeValueAsString(event2);

        // When: Send for both customers
        kafkaTemplate.send("order.events", ORDER_ID + "-1", payload1).get(5, TimeUnit.SECONDS);
        kafkaTemplate.send("order.events", ORDER_ID + "-2", payload2).get(5, TimeUnit.SECONDS);
        
        // And send duplicates
        kafkaTemplate.send("order.events", ORDER_ID + "-1", payload1).get(5, TimeUnit.SECONDS);
        kafkaTemplate.send("order.events", ORDER_ID + "-2", payload2).get(5, TimeUnit.SECONDS);

        // Then: Both processed (different tenant isolation)
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            long count = processedEventRepository.count();
            assertThat(count).isEqualTo(2);
        });

        var processed = processedEventRepository.findAll();
        assertThat(processed).extracting(ProcessedEventEntity::getCustomerId)
                .containsExactlyInAnyOrder(CUSTOMER_ID + "-1", CUSTOMER_ID + "-2");
    }
}