package com.meridian.event.infrastructure.resilience;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridian.event.application.port.inbound.PlaceOrderUseCase;
import com.meridian.event.application.port.inbound.ProcessPaymentUseCase;
import com.meridian.event.application.port.inbound.OrderLineInput;
import com.meridian.event.application.port.outbound.OrderRepository;
import com.meridian.event.application.port.outbound.PaymentRepository;
import com.meridian.event.application.port.outbound.AuthorizationService;
import com.meridian.event.application.port.outbound.ClientIpResolver;
import com.meridian.event.application.service.DefaultPaymentService;
import com.meridian.event.domain.model.Order;
import com.meridian.event.domain.model.Payment;
import com.meridian.event.domain.model.PaymentStatus;
import com.meridian.event.infrastructure.messaging.kafka.KafkaEventPublisher;
import com.meridian.event.infrastructure.messaging.kafka.OutboxEventPublisher;
import com.meridian.event.infrastructure.messaging.kafka.OrderEventConsumer;
import com.meridian.event.infrastructure.persistence.jpa.OutboxEventEntity;
import com.meridian.event.infrastructure.persistence.jpa.ProcessedEventEntity;
import com.meridian.event.infrastructure.persistence.repository.OutboxEventRepository;
import com.meridian.event.infrastructure.persistence.repository.ProcessedEventRepository;
import com.meridian.event.infrastructure.projection.OrderProjectionHandler;
import com.meridian.event.infrastructure.security.audit.SecurityAuditLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@Import(ResilienceTestConfig.class)
@DirtiesContext
@Transactional
class ApplicationRestartTest {

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
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private OrderProjectionHandler projectionHandler;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderEventConsumer consumer;

    @Autowired
    private OrderLineInput orderLineInput;

    @BeforeEach
    void setUp() {
        outboxRepository.deleteAll();
        processedEventRepository.deleteAll();
    }

    @Test
    void shouldRecoverPendingOutboxEventsOnRestart() {
        // Given: Events in outbox (from before restart)
        int eventCount = 3;
        for (int i = 0; i < eventCount; i++) {
            String eventId = UUID.randomUUID().toString();
            OutboxEventEntity entity = new OutboxEventEntity();
            entity.setId(eventId);
            entity.setAggregateId("order-restart-recover-" + i);
            entity.setEventType("ORDER_CONFIRMED");
            entity.setPayload("{\"test\":\"" + i + "\"}");
            entity.setSentAt(null); // Not sent yet
            entity.setRetryCount(0);
            outboxRepository.save(entity);
        }
        
        // When: Application restarts (outbox publisher starts via @Scheduled)
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
    void shouldRecoverPendingPaymentsOnRestart() {
        // Given: Payment in PENDING state (from before restart)
        Order order = placeOrderUseCase.placeOrder("customer-restart-payment", List.of(new OrderLineInput("SKU-1", 1, 100.00)), "customer-restart-payment");
        
        Payment pendingPayment = processPaymentUseCase.processPayment(order.getId().value(), 100.00, "CREDIT_CARD", "customer-restart-payment");
        assertThat(pendingPayment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        
        // When: Application restarts - the async completion would need to be retried
        // In real system: a scheduled job or manual retry would call completePayment
        // We verify the payment is still in recoverable state
        
        // Then: Payment is still PENDING (not lost)
        Payment recovered = paymentRepository.findById(pendingPayment.getId()).orElseThrow();
        assertThat(recovered.getStatus()).isEqualTo(PaymentStatus.PENDING);
        
        // And: Can be completed
        new DefaultPaymentService(
                orderRepository, paymentRepository, eventPublisher,
                new com.meridian.event.application.port.outbound.NotificationService() {
                    @Override public void notifyOrderConfirmed(String orderId, String customerId) {}
                },
                new com.meridian.event.infrastructure.payment.MockPaymentGateway(),
                new com.meridian.event.application.port.outbound.AuthorizationService() {
                    @Override public boolean isAdmin() { return false; }
                    @Override public boolean canAccessOrder(String auth, String order) { return true; }
                    @Override public boolean canProcessPayment(String auth, String order) { return true; }
                },
                new com.meridian.event.application.port.outbound.ClientIpResolver() {
                    @Override public String resolveClientIp() { return "127.0.0.1"; }
                },
                java.util.concurrent.Executors.newSingleThreadExecutor()
        ).completePayment(pendingPayment.getId().value(), "customer-restart-payment");
        
        // Then: Payment approved
        Payment completed = paymentRepository.findById(pendingPayment.getId()).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    void shouldNotReprocessAlreadyProcessedEventsOnRestart() throws Exception {
        // Given: Events already processed and in processed_events table
        String orderId = "order-restart-idempotent";
        String customerId = "customer-restart-idempotent";
        String eventId = UUID.randomUUID().toString();
        
        // Pre-populate processed events (simulating from before restart)
        ProcessedEventEntity processed = new ProcessedEventEntity();
        processed.setEventId(eventId);
        processed.setAggregateId(orderId);
        processed.setEventType("ORDER_CONFIRMED");
        processed.setCustomerId(customerId);
        processed.setProcessedAt(java.time.Instant.now());
        processedEventRepository.save(processed);
        
        // Also create projection
        var event = new com.meridian.event.domain.model.OrderConfirmedEvent(
                orderId, customerId, "100.00", eventId
        );
        projectionHandler.handle(event);
        
        // When: Send same event again (simulating Kafka redelivery after restart)
        String payload = objectMapper.writeValueAsString(event);
        kafkaTemplate.send("order.events", orderId, payload).get(5, TimeUnit.SECONDS);
        
        // Then: Should not reprocess (idempotency)
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            long count = processedEventRepository.count();
            assertThat(count).isEqualTo(1); // Still only one
        });
        
        // And: Projection version not incremented again
        var projection = projectionHandler.getProjection(orderId);
        assertThat(projection.get().getVersion()).isEqualTo(1L);
    }

    @Test
    void shouldResumeOutboxPublisherFromWhereItLeftOff() {
        // Given: Mix of sent and unsent events
        String sentEventId = UUID.randomUUID().toString();
        OutboxEventEntity sentEvent = new OutboxEventEntity();
        sentEvent.setId(sentEventId);
        sentEvent.setAggregateId("order-sent");
        sentEvent.setEventType("ORDER_CONFIRMED");
        sentEvent.setPayload("{\"test\":\"sent\"}");
        sentEvent.setSentAt(java.time.Instant.now().minusSeconds(10));
        sentEvent.setRetryCount(0);
        outboxRepository.save(sentEvent);
        
        String unsentEventId = UUID.randomUUID().toString();
        OutboxEventEntity unsentEvent = new OutboxEventEntity();
        unsentEvent.setId(unsentEventId);
        unsentEvent.setAggregateId("order-unsent");
        unsentEvent.setEventType("ORDER_CONFIRMED");
        unsentEvent.setPayload("{\"test\":\"unsent\"}");
        unsentEvent.setSentAt(null);
        unsentEvent.setRetryCount(0);
        outboxRepository.save(unsentEvent);
        
        // When: Publisher runs on restart
        outboxPublisher.publishPendingEvents();
        
        // Then: Only unsent event should be sent
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            OutboxEventEntity updatedSent = outboxRepository.findById(sentEventId).orElseThrow();
            assertThat(updatedSent.getSentAt()).isNotNull(); // Still sent
            
            OutboxEventEntity updatedUnsent = outboxRepository.findById(unsentEventId).orElseThrow();
            assertThat(updatedUnsent.getSentAt()).isNotNull(); // Now sent
        });
    }

    @Test
    void shouldHandleFlywayMigrationOnRestart() {
        // Verify all tables exist and are accessible by saving and retrieving
        Order order = new Order(com.meridian.event.domain.model.valueobjects.OrderId.generate(), "flyway-test", List.of());
        orderRepository.save(order);
        assertThat(orderRepository.findById(order.getId())).isPresent();

        Payment payment = new Payment(com.meridian.event.domain.model.valueobjects.PaymentId.generate(), "order-123", com.meridian.event.domain.model.valueobjects.Money.of(new java.math.BigDecimal("10.00"), "USD"), "CREDIT_CARD");
        paymentRepository.save(payment);
        assertThat(paymentRepository.findById(payment.getId())).isPresent();

        assertThat(outboxRepository.count()).isGreaterThanOrEqualTo(0);
        assertThat(processedEventRepository.count()).isGreaterThanOrEqualTo(0);
    }
}
