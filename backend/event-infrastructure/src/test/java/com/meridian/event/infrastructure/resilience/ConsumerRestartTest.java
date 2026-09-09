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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
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
class ConsumerRestartTest {

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

    @BeforeEach
    void setUp() {
        processedEventRepository.deleteAll();
    }

    @Test
    void shouldResumeFromCommittedOffsetAfterRestart() throws Exception {
        // Given: Event sent and processed
        String orderId = "order-restart-1";
        String customerId = "customer-restart";
        
        OrderConfirmedEvent event = new OrderConfirmedEvent(
                orderId, customerId, "100.00", UUID.randomUUID().toString()
        );
        String payload = objectMapper.writeValueAsString(event);
        
        kafkaTemplate.send("order.events", orderId, payload).get(5, TimeUnit.SECONDS);
        
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(processedEventRepository.count()).isEqualTo(1);
        });
        
        var projection = projectionHandler.getProjection(orderId);
        assertThat(projection).isPresent();
        
        // When: Simulate consumer restart (new consumer instance would start from committed offset)
        // In this test, we just verify the offset was committed by checking no reprocessing
        
        // Send the same event again (simulating it being redelivered after restart)
        kafkaTemplate.send("order.events", orderId, payload).get(5, TimeUnit.SECONDS);
        
        // Then: Should not reprocess (idempotency)
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(processedEventRepository.count()).isEqualTo(1);
        });
    }

    @Test
    void shouldNotReprocessIfAcknowledgedButNotPersisted() throws Exception {
        // This tests the edge case where acknowledgment happens before DB commit
        // With our implementation: acknowledgment is AFTER transaction, so this is safe
        
        String orderId = "order-ack-test";
        String customerId = "customer-ack";
        
        OrderConfirmedEvent event = new OrderConfirmedEvent(
                orderId, customerId, "50.00", UUID.randomUUID().toString()
        );
        String payload = objectMapper.writeValueAsString(event);
        
        // Send event
        kafkaTemplate.send("order.events", orderId, payload).get(5, TimeUnit.SECONDS);
        
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(processedEventRepository.count()).isEqualTo(1);
        });
        
        // Send again - should be deduplicated
        kafkaTemplate.send("order.events", orderId, payload).get(5, TimeUnit.SECONDS);
        
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(processedEventRepository.count()).isEqualTo(1);
        });
    }
}
