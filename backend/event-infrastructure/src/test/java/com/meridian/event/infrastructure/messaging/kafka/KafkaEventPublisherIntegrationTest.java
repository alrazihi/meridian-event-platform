package com.meridian.event.infrastructure.messaging.kafka;

import com.meridian.event.application.port.outbound.EventPublisher;
import com.meridian.event.domain.model.DomainEvent;
import com.meridian.event.domain.model.OrderConfirmedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

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

    @Autowired
    private ConsumerFactory<String, String> consumerFactory;

    @Test
    void shouldPublishEventToKafka() {
        String orderId = "order-pub-" + UUID.randomUUID();
        OrderConfirmedEvent event = new OrderConfirmedEvent(
                orderId, "customer-pub", "100.00", UUID.randomUUID().toString()
        );

        eventPublisher.publish(event);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            var records = com.meridian.event.infrastructure.resilience.KafkaTestHelper.getRecords(
                    consumerFactory, "order.events", 5000, 1
            );
            assertThat(records).isNotEmpty();
            assertThat(records.iterator().next().value()).contains(orderId);
        });
    }
}
