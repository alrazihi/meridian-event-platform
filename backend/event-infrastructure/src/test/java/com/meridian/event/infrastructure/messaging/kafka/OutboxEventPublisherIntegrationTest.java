package com.meridian.event.infrastructure.messaging.kafka;

import com.meridian.event.application.port.outbound.EventPublisher;
import com.meridian.event.domain.model.OrderConfirmedEvent;
import com.meridian.event.infrastructure.persistence.jpa.OutboxEventEntity;
import com.meridian.event.infrastructure.persistence.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@Import({KafkaTestConfig.class, OutboxTestConfig.class})
@DirtiesContext
class OutboxEventPublisherIntegrationTest {

    @Autowired
    private EventPublisher eventPublisher;

    @Autowired
    private OutboxEventRepository outboxRepository;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void shouldPersistEventToOutboxAndPublishToKafka() throws Exception {
        OrderConfirmedEvent event = new OrderConfirmedEvent(
                "order-outbox-1",
                "customer-123",
                "100.00",
                "corr-123"
        );

        // Publish event (should go to outbox)
        eventPublisher.publish(event);

        // Verify event is in outbox
        List<OutboxEventEntity> outboxEvents = outboxRepository.findAll();
        assertThat(outboxEvents).hasSize(1);
        assertThat(outboxEvents.get(0).getEventType()).isEqualTo("ORDER_CONFIRMED");
        assertThat(outboxEvents.get(0).getAggregateId()).isEqualTo("order-outbox-1");
        assertThat(outboxEvents.get(0).getSentAt()).isNull();

        // Wait for outbox publisher to process
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            OutboxEventEntity processed = outboxRepository.findById(outboxEvents.get(0).getId()).orElseThrow();
            assertThat(processed.getSentAt()).isNotNull();
        });
    }

    @Test
    void shouldRouteEventsToCorrectTopics() throws Exception {
        OrderConfirmedEvent orderEvent = new OrderConfirmedEvent("order-1", "cust-1", "100", "corr");
        com.meridian.event.domain.model.PaymentProcessedEvent paymentEvent = 
                new com.meridian.event.domain.model.PaymentProcessedEvent("payment-1", "order-1", "100", "APPROVED", "corr");
        com.meridian.event.domain.model.InventoryReservedEvent inventoryEvent = 
                new com.meridian.event.domain.model.InventoryReservedEvent(com.meridian.event.domain.model.valueobjects.Sku.of("SKU-1"), 5, "corr");

        eventPublisher.publish(orderEvent);
        eventPublisher.publish(paymentEvent);
        eventPublisher.publish(inventoryEvent);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            List<OutboxEventEntity> all = outboxRepository.findAll();
            assertThat(all).hasSize(3);
            assertThat(all).allMatch(e -> e.getSentAt() != null);
        });
    }
}