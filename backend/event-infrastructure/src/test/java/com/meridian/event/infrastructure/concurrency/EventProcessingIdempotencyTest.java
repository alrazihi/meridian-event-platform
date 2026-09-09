package com.meridian.event.infrastructure.concurrency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridian.event.domain.model.DomainEvent;
import com.meridian.event.domain.model.OrderConfirmedEvent;
import com.meridian.event.infrastructure.messaging.kafka.OrderEventConsumer;
import com.meridian.event.infrastructure.persistence.jpa.ProcessedEventEntity;
import com.meridian.event.infrastructure.persistence.repository.ProcessedEventRepository;
import com.meridian.event.infrastructure.projection.OrderProjectionHandler;
import com.meridian.event.infrastructure.messaging.kafka.KafkaTestConfig;
import com.meridian.event.infrastructure.messaging.kafka.ConsumerTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@Import({KafkaTestConfig.class, ConsumerTestConfig.class})
@DirtiesContext
@EmbeddedKafka(partitions = 1, topics = {"order.events", "dlq.order.events"})
class EventProcessingIdempotencyTest {

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

    private static final String ORDER_ID = "order-idempotency-123";
    private static final String CUSTOMER_ID = "customer-456";
    private static final String EVENT_ID = "event-idempotency-123";

    @BeforeEach
    void setUp() {
        processedEventRepository.deleteAll();
    }

    @Test
    void shouldProcessEventExactlyOnceUnderConcurrentDelivery() throws Exception {
        int deliveryCount = 20;
        
        OrderConfirmedEvent event = new OrderConfirmedEvent(
                ORDER_ID,
                CUSTOMER_ID,
                "250.00",
                EVENT_ID
        );
        String payload = objectMapper.writeValueAsString(event);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(deliveryCount);
        ExecutorService executor = Executors.newFixedThreadPool(deliveryCount);
        AtomicInteger processedCount = new AtomicInteger(0);
        AtomicInteger duplicateCount = new AtomicInteger(0);

        for (int i = 0; i < deliveryCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    kafkaTemplate.send("order.events", ORDER_ID, payload).get(5, TimeUnit.SECONDS);
                    processedCount.incrementAndGet();
                } catch (Exception e) {
                    duplicateCount.incrementAndGet();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertThat(endLatch.await(30, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            long count = processedEventRepository.count();
            assertThat(count).isEqualTo(1);
        });

        var projection = projectionHandler.getProjection(ORDER_ID);
        assertThat(projection).isPresent();
        assertThat(projection.get().getCustomerId()).isEqualTo(CUSTOMER_ID);
        assertThat(projection.get().getTotal().toString()).isEqualTo("250.00");
        assertThat(projection.get().getVersion()).isEqualTo(1L);
    }

    @Test
    void shouldMaintainIdempotencyAcrossDifferentConsumers() throws Exception {
        OrderConfirmedEvent event = new OrderConfirmedEvent(
                ORDER_ID,
                CUSTOMER_ID,
                "100.00",
                EVENT_ID
        );
        String payload = objectMapper.writeValueAsString(event);

        int consumerCount = 5;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(consumerCount);
        ExecutorService executor = Executors.newFixedThreadPool(consumerCount);

        for (int i = 0; i < consumerCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < 4; j++) {
                        kafkaTemplate.send("order.events", ORDER_ID, payload).get(5, TimeUnit.SECONDS);
                        Thread.sleep(10);
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

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            long count = processedEventRepository.count();
            assertThat(count).isEqualTo(1);
        });
    }

    @Test
    void shouldIsolateIdempotencyByCustomerId() throws Exception {
        String eventId1 = "event-customer-1";
        String eventId2 = "event-customer-2";

        OrderConfirmedEvent event1 = new OrderConfirmedEvent(
                ORDER_ID + "-1", CUSTOMER_ID + "-1", "100.00", eventId1
        );
        OrderConfirmedEvent event2 = new OrderConfirmedEvent(
                ORDER_ID + "-2", CUSTOMER_ID + "-2", "200.00", eventId2
        );

        String payload1 = objectMapper.writeValueAsString(event1);
        String payload2 = objectMapper.writeValueAsString(event2);

        // Send events concurrently for different customers
        kafkaTemplate.send("order.events", ORDER_ID + "-1", payload1).get(5, TimeUnit.SECONDS);
        kafkaTemplate.send("order.events", ORDER_ID + "-2", payload2).get(5, TimeUnit.SECONDS);

        // Send duplicates
        kafkaTemplate.send("order.events", ORDER_ID + "-1", payload1).get(5, TimeUnit.SECONDS);
        kafkaTemplate.send("order.events", ORDER_ID + "-2", payload2).get(5, TimeUnit.SECONDS);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            long count = processedEventRepository.count();
            assertThat(count).isEqualTo(2);
        });

        var processed = processedEventRepository.findAll();
        assertThat(processed).hasSize(2);
        assertThat(processed).extracting(ProcessedEventEntity::getCustomerId)
                .containsExactlyInAnyOrder(CUSTOMER_ID + "-1", CUSTOMER_ID + "-2");
    }
}
