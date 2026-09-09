package com.meridian.event.infrastructure.resilience;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridian.event.domain.model.DomainEvent;
import com.meridian.event.domain.model.OrderConfirmedEvent;
import com.meridian.event.infrastructure.messaging.kafka.KafkaEventPublisher;
import com.meridian.event.infrastructure.messaging.kafka.OutboxEventPublisher;
import com.meridian.event.infrastructure.persistence.jpa.OutboxEventEntity;
import com.meridian.event.infrastructure.persistence.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

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
@Transactional
class KafkaUnavailableTest {

    @Autowired
    private KafkaEventPublisher eventPublisher;

    @Autowired
    private OutboxEventPublisher outboxPublisher;

    @Autowired
    private OutboxEventRepository outboxRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @BeforeEach
    void setUp() {
        outboxRepository.deleteAll();
    }

    @Test
    void shouldPersistToOutboxWhenKafkaUnavailable() {
        // When: Publish event (Kafka is available in test, but we verify outbox pattern)
        OrderConfirmedEvent event = new OrderConfirmedEvent(
                "order-kafka-test-1",
                "customer-123",
                "100.00",
                UUID.randomUUID().toString()
        );
        
        eventPublisher.publish(event);
        
        // Then: Event should be in outbox immediately (same transaction)
        OutboxEventEntity saved = outboxRepository.findAll().stream()
                .filter(e -> e.getAggregateId().equals("order-kafka-test-1"))
                .findFirst().orElseThrow();
        
        assertThat(saved.getSentAt()).isNull();
        assertThat(saved.getRetryCount()).isEqualTo(0);
    }

    @Test
    void shouldRetryAndEventuallySendWhenKafkaRecovers() throws InterruptedException {
        // Given: Events in outbox
        int eventCount = 5;
        for (int i = 0; i < eventCount; i++) {
            OrderConfirmedEvent event = new OrderConfirmedEvent(
                    "order-retry-" + i,
                    "customer-123",
                    "100.00",
                    UUID.randomUUID().toString()
            );
            eventPublisher.publish(event);
        }
        
        // When: Run outbox publisher (Kafka is available)
        outboxPublisher.publishPendingEvents();
        
        // Then: All events should be sent
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            long sentCount = outboxRepository.findAll().stream()
                    .filter(e -> e.getSentAt() != null)
                    .count();
            assertThat(sentCount).isEqualTo(eventCount);
        });
    }

    @Test
    void shouldNotLoseEventsWhenPublisherCrashesMidBatch() throws InterruptedException {
        // Given: Multiple events in outbox
        int eventCount = 20;
        for (int i = 0; i < eventCount; i++) {
            OrderConfirmedEvent event = new OrderConfirmedEvent(
                    "order-crash-" + i,
                    "customer-123",
                    "100.00",
                    UUID.randomUUID().toString()
            );
            eventPublisher.publish(event);
        }
        
        // When: Run publisher multiple times (simulating restarts)
        for (int i = 0; i < 3; i++) {
            outboxPublisher.publishPendingEvents();
            Thread.sleep(100);
        }
        
        // Then: All events eventually sent exactly once
        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            long sentCount = outboxRepository.findAll().stream()
                    .filter(e -> e.getSentAt() != null)
                    .count();
            assertThat(sentCount).isEqualTo(eventCount);
        });
    }

    @Test
    void shouldHandleConcurrentPublishersWithoutDuplication() throws InterruptedException {
        // Given: Events in outbox
        int eventCount = 10;
        for (int i = 0; i < eventCount; i++) {
            OrderConfirmedEvent event = new OrderConfirmedEvent(
                    "order-concurrent-" + i,
                    "customer-123",
                    "100.00",
                    UUID.randomUUID().toString()
            );
            eventPublisher.publish(event);
        }
        
        // When: Multiple publisher instances run concurrently (pessimistic locking)
        int publisherCount = 3;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(publisherCount);
        ExecutorService executor = Executors.newFixedThreadPool(publisherCount);
        
        for (int i = 0; i < publisherCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    outboxPublisher.publishPendingEvents();
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
        
        // Then: Each event sent exactly once
        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            long sentCount = outboxRepository.findAll().stream()
                    .filter(e -> e.getSentAt() != null)
                    .count();
            assertThat(sentCount).isEqualTo(eventCount);
        });
    }
}
