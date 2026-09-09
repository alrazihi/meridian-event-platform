package com.meridian.event.infrastructure.concurrency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridian.event.domain.model.DomainEvent;
import com.meridian.event.domain.model.OrderConfirmedEvent;
import com.meridian.event.infrastructure.messaging.kafka.KafkaEventPublisher;
import com.meridian.event.infrastructure.messaging.kafka.OutboxEventPublisher;
import com.meridian.event.infrastructure.persistence.jpa.OutboxEventEntity;
import com.meridian.event.infrastructure.persistence.repository.OutboxEventRepository;
import com.meridian.event.infrastructure.config.TestConfig;
import com.meridian.event.infrastructure.persistence.adapter.RepositoryTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
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
@Import({RepositoryTestConfig.class, TestConfig.class})
@DirtiesContext
@Transactional
class OutboxPublisherConcurrencyTest {

    @Autowired
    private KafkaEventPublisher eventPublisher;

    @Autowired
    private OutboxEventPublisher outboxPublisher;

    @Autowired
    private OutboxEventRepository outboxRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        outboxRepository.deleteAll();
    }

    @Test
    void shouldNotDuplicateEventsWhenMultiplePublishersRun() throws InterruptedException {
        int eventCount = 20;
        int publisherThreads = 3;

        // First, publish events to outbox
        for (int i = 0; i < eventCount; i++) {
            OrderConfirmedEvent event = new OrderConfirmedEvent(
                    "order-" + i,
                    "customer-123",
                    "100.00",
                    UUID.randomUUID().toString()
            );
            eventPublisher.publish(event);
        }

        // Verify all events are in outbox
        assertThat(outboxRepository.countBySentAtIsNull()).isEqualTo(eventCount);

        // Now run multiple publisher instances concurrently
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(publisherThreads);
        ExecutorService executor = Executors.newFixedThreadPool(publisherThreads);
        AtomicInteger publishedCount = new AtomicInteger(0);

        for (int i = 0; i < publisherThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    // Each publisher runs one batch
                    outboxPublisher.publishPendingEvents();
                    publishedCount.incrementAndGet();
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

        // Wait for all events to be processed
        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            long unsent = outboxRepository.countBySentAtIsNull();
            assertThat(unsent).isEqualTo(0);
        });

        // Each event should be sent exactly once
        long sentCount = outboxRepository.findAll().stream()
                .filter(e -> e.getSentAt() != null)
                .count();
        assertThat(sentCount).isEqualTo(eventCount);
    }

    @Test
    void shouldHandleConcurrentPublishAndRetry() throws InterruptedException {
        // Publish an event that will fail (invalid topic)
        OrderConfirmedEvent event = new OrderConfirmedEvent(
                "order-retry",
                "customer-123",
                "100.00",
                UUID.randomUUID().toString()
        );
        eventPublisher.publish(event);

        // Run publisher with retries
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        for (int i = 0; i < 2; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < 3; j++) {
                        outboxPublisher.publishPendingEvents();
                        Thread.sleep(100);
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

        // Event should eventually be in DLQ (after max retries)
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            var events = outboxRepository.findAll();
            assertThat(events).hasSize(1);
            OutboxEventEntity entity = events.get(0);
            assertThat(entity.getRetryCount()).isGreaterThanOrEqualTo(5);
            assertThat(entity.getSentAt()).isNotNull();
        });
    }
}
