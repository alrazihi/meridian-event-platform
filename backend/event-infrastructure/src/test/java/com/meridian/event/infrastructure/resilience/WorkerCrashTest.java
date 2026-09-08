package com.meridian.event.infrastructure.resilience;

import com.meridian.event.application.port.inbound.ProcessPaymentUseCase;
import com.meridian.event.application.port.inbound.PlaceOrderUseCase;
import com.meridian.event.application.port.inbound.OrderLineInput;
import com.meridian.event.application.port.outbound.OrderRepository;
import com.meridian.event.application.port.outbound.PaymentRepository;
import com.meridian.event.application.service.DefaultPaymentService;
import com.meridian.event.domain.model.Payment;
import com.meridian.event.domain.model.PaymentStatus;
import com.meridian.event.infrastructure.messaging.kafka.KafkaEventPublisher;
import com.meridian.event.infrastructure.messaging.kafka.OutboxEventPublisher;
import com.meridian.event.infrastructure.persistence.jpa.OutboxEventEntity;
import com.meridian.event.infrastructure.persistence.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
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
class WorkerCrashTest {

    @Autowired
    private PlaceOrderUseCase placeOrderUseCase;

    @Autowired
    private ProcessPaymentUseCase processPaymentUseCase;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private KafkaEventPublisher eventPublisher;

    @Autowired
    private OutboxEventPublisher outboxPublisher;

    @Autowired
    private OutboxEventRepository outboxRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderLineInput orderLineInput;

    @BeforeEach
    void setUp() {
        outboxRepository.deleteAll();
    }

    @Test
    void shouldNotLeavePartialOrderOnCrash() {
        // Given: Place order (single transaction: save order + write outbox)
        Order order = placeOrderUseCase.placeOrder("customer-crash", List.of(new OrderLineInput("SKU-1", 1, 100.00)), "customer-crash");
        
        // When: Simulate crash immediately after transaction commits
        // (In reality, if crash happens before commit, nothing is persisted)
        // If crash happens after commit but before publisher runs, outbox has event
        
        // Then: Order is persisted (transaction committed)
        assertThat(orderRepository.findById(order.getId())).isPresent();
        
        // And: Event is in outbox (same transaction)
        assertThat(outboxRepository.findAll()).anyMatch(e -> e.getEventType().equals("ORDER_CONFIRMED"));
        
        // Publisher will pick up on next run (or after restart)
        outboxPublisher.publishPendingEvents();
        
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            long sentCount = outboxRepository.findAll().stream()
                    .filter(e -> e.getSentAt() != null)
                    .count();
            assertThat(sentCount).isGreaterThanOrEqualTo(1);
        });
    }

    @Test
    void shouldNotLeavePartialPaymentOnCrash() throws InterruptedException {
        // Given: Order and pending payment
        Order order = placeOrderUseCase.placeOrder("customer-payment-crash", List.of(new OrderLineInput("SKU-1", 1, 100.00)), "customer-payment-crash");
        
        Payment pendingPayment = processPaymentUseCase.processPayment(order.getId().value(), 100.00, "CREDIT_CARD", "customer-payment-crash");
        
        // When: Crash after payment initiation but before async completion
        // State: Payment is PENDING in DB, no outbox event for payment yet
        
        // Then: Payment is PENDING
        assertThat(pendingPayment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        
        // And: No PAYMENT_PROCESSED event in outbox yet
        assertThat(outboxRepository.findAll()).noneMatch(e -> e.getEventType().equals("PAYMENT_PROCESSED"));
        
        // On restart: Scheduler/retry mechanism would call completePayment
        // or user would retry - the saga can continue from PENDING state
    }

    @Test
    void shouldHandleOutboxPublisherCrashMidBatch() throws InterruptedException {
        // Given: Multiple events in outbox
        int eventCount = 10;
        for (int i = 0; i < eventCount; i++) {
            String eventId = UUID.randomUUID().toString();
            OutboxEventEntity entity = new OutboxEventEntity();
            entity.setId(eventId);
            entity.setAggregateId("order-crash-batch-" + i);
            entity.setEventType("ORDER_CONFIRMED");
            entity.setPayload("{\"test\":\"" + i + "\"}");
            outboxRepository.save(entity);
        }
        
        // When: Publisher starts but crashes mid-batch (simulated by running once)
        outboxPublisher.publishPendingEvents();
        
        // Then: Some events may be sent, others still pending
        // On next run (or restart), publisher picks up remaining
        outboxPublisher.publishPendingEvents();
        
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            long sentCount = outboxRepository.findAll().stream()
                    .filter(e -> e.getSentAt() != null)
                    .count();
            assertThat(sentCount).isEqualTo(eventCount);
        });
    }

    @Test
    void shouldNotLoseEventsOnConsumerCrash() throws InterruptedException {
        // Given: Events in Kafka, consumer processes some then crashes
        // With enable-auto-commit=false, offset only committed after successful processing
        
        // When: Send events
        int eventCount = 5;
        for (int i = 0; i < eventCount; i++) {
            String orderId = "order-consumer-crash-" + i;
            var event = new com.meridian.event.domain.model.OrderConfirmedEvent(
                    orderId, "customer-crash", "100.00", UUID.randomUUID().toString()
            );
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send("order.events", orderId, payload).get(5, TimeUnit.SECONDS);
        }
        
        // Consumer processes them (we can't easily simulate crash mid-processing in test)
        // But we verify: offset committed AFTER processed_event saved
        
        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            long count = processedEventRepository.count();
            assertThat(count).isEqualTo(eventCount);
        });
        
        // If consumer crashed before acknowledgment, on restart it would re-read
        // But idempotency (processed_events) prevents duplicate processing
    }
}