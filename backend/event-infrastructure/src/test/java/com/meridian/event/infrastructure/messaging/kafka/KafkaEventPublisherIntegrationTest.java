package com.meridian.event.infrastructure.messaging.kafka;

import com.meridian.event.application.port.outbound.EventPublisher;
import com.meridian.event.domain.model.DomainEvent;
import com.meridian.event.domain.model.OrderConfirmedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@Import(KafkaTestConfig.class)
@DirtiesContext
@EmbeddedKafka(partitions = 1, topics = {"order.events", "payment.events", "inventory.events"})
class KafkaEventPublisherIntegrationTest {

    @Autowired
    private EventPublisher eventPublisher;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void shouldPublishEventToKafka() {
        OrderConfirmedEvent event = new OrderConfirmedEvent(
                "order-123",
                "customer-456",
                "100.00",
                "corr-123"
        );

        eventPublisher.publish(event);

        // Verify event was sent by consuming it
        org.springframework.kafka.test.utils.KafkaTestUtils.getRecords(
                kafkaTemplate.getDefaultTopic(), 1, 5000, TimeUnit.MILLISECONDS
        );
    }
}