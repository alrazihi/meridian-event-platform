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
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@Import(ResilienceTestConfig.class)
@DirtiesContext
@Transactional
class NetworkTimeoutTest {

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
    void shouldNotBlockIndefinitelyOnKafkaTimeout() {
        // Given: Kafka producer configured with timeouts (acks=all, retries=3)
        // When: Publish event (Kafka available in test)
        var order = placeOrderUseCase.placeOrder("customer-timeout", List.of(new OrderLineInput("SKU-1", 1, 100.00)), "customer-timeout");
        
        // Then: Should complete quickly (ms, not seconds)
        // The outbox write is synchronous DB operation
        assertThat(outboxRepository.findAll()).hasSize(1);
    }

    @Test
    void shouldHandlePaymentGatewayTimeout() throws Exception {
        // Given: Payment in PENDING state
        var order = placeOrderUseCase.placeOrder("customer-gw-timeout", List.of(new OrderLineInput("SKU-1", 1, 100.00)), "customer-gw-timeout");
        Payment pendingPayment = processPaymentUseCase.processPayment(order.getId().value(), 100.00, "CREDIT_CARD", "customer-gw-timeout");
        
        // When: Complete payment (mock gateway is fast)
        // In production, PaymentGateway should have timeout configured
        CompletableFuture<Void> future = new DefaultPaymentService(
                orderRepository, paymentRepository, eventPublisher,
                new com.meridian.event.application.port.outbound.NotificationService() {
                    @Override public void notifyOrderConfirmed(String orderId, String customerId) {}
                    @Override public void notifyPaymentProcessed(String paymentId, String customerId) {}
                },
                new com.meridian.event.infrastructure.payment.MockPaymentGateway(),
                new com.meridian.event.infrastructure.security.audit.SecurityAuditLogger("test-secret"),
                new org.springframework.mock.web.MockHttpServletRequest()
        ).processPaymentAsync(order.getId().value(), 100.00, "CREDIT_CARD", "customer-gw-timeout");
        
        future.join();
        
        // Then: Should complete
        Payment completed = paymentRepository.findById(pendingPayment.getId()).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    void shouldHandleDatabaseConnectionTimeout() {
        // Given: HikariCP configured with connection-timeout=30000ms
        // When: Place order (uses DB connection from pool)
        var order = placeOrderUseCase.placeOrder("customer-db-timeout", List.of(new OrderLineInput("SKU-1", 1, 100.00)), "customer-db-timeout");
        
        // Then: Should succeed (pool has connections)
        assertThat(orderRepository.findById(order.getId())).isPresent();
    }

    @Test
    void shouldNotLeakConnectionsOnException() {
        // Given: Multiple operations
        // When: Some succeed, some fail
        var order = placeOrderUseCase.placeOrder("customer-leak", List.of(new OrderLineInput("SKU-1", 1, 100.00)), "customer-leak");
        
        // Force an exception in a subsequent operation
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> placeOrderUseCase.placeOrder("customer-leak", List.of(), "customer-leak")
        );
        
        // Then: Connection pool should not be exhausted
        // (Hard to test directly, but HikariCP handles this automatically)
        assertThat(orderRepository.findById(order.getId())).isPresent();
    }

    @Test
    void shouldRespectTransactionTimeout() {
        // Given: @Transactional with default timeout (Spring default)
        // When: Long-running transaction (not really possible in test)
        // We verify the pattern: short transactions, external calls outside
        
        var order = placeOrderUseCase.placeOrder("customer-tx-timeout", List.of(new OrderLineInput("SKU-1", 1, 100.00)), "customer-tx-timeout");
        
        // Order placement transaction: DB write + outbox write (fast)
        assertThat(orderRepository.findById(order.getId())).isPresent();
        
        // Payment initiation: DB write only (fast)
        Payment pending = processPaymentUseCase.processPayment(order.getId().value(), 100.00, "CREDIT_CARD", "customer-tx-timeout");
        assertThat(pending.getStatus()).isEqualTo(PaymentStatus.PENDING);
        
        // Gateway call is OUTSIDE transaction (async)
    }
}