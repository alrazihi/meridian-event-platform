package com.meridian.event.application.service;

import com.meridian.event.application.port.inbound.ProcessPaymentUseCase;
import com.meridian.event.application.port.outbound.AuthorizationService;
import com.meridian.event.application.port.outbound.ClientIpResolver;
import com.meridian.event.application.port.outbound.EventPublisher;
import com.meridian.event.application.port.outbound.NotificationService;
import com.meridian.event.application.port.outbound.OrderRepository;
import com.meridian.event.application.port.outbound.PaymentGateway;
import com.meridian.event.application.port.outbound.PaymentRepository;
import com.meridian.event.domain.exception.AuthorizationException;
import com.meridian.event.domain.model.Order;
import com.meridian.event.domain.model.Payment;
import com.meridian.event.domain.model.PaymentStatus;
import com.meridian.event.domain.model.valueobjects.Money;
import com.meridian.event.domain.model.valueobjects.OrderId;
import com.meridian.event.domain.model.valueobjects.PaymentId;
import com.meridian.event.infrastructure.payment.MockPaymentGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.concurrent.DelegatingSecurityContextExecutor;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceSecurityTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private NotificationService notificationService;

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private ClientIpResolver clientIpResolver;

    private DefaultPaymentService paymentService;

    @BeforeEach
    void setUp() {
        MockPaymentGateway mockGateway = new MockPaymentGateway();
        Executor executor = new DelegatingSecurityContextExecutor(java.util.concurrent.Executors.newSingleThreadExecutor());

        paymentService = new DefaultPaymentService(
                orderRepository,
                paymentRepository,
                eventPublisher,
                notificationService,
                mockGateway,
                authorizationService,
                clientIpResolver,
                executor
        );

        when(authorizationService.canProcessPayment(any(), any())).thenReturn(true);
    }

    @Test
    void shouldAllowCustomerToProcessPaymentForOwnOrder() {
        String customerId = "customer-123";
        String orderId = "order-123";
        String authenticatedCustomerId = "customer-123";

        Order order = new Order(OrderId.from(orderId), customerId, List.of());
        when(orderRepository.findById(OrderId.from(orderId))).thenReturn(Optional.of(order));
        when(paymentRepository.existsByOrderIdAndStatus(orderId, PaymentStatus.APPROVED)).thenReturn(false);

        Payment savedPayment = new Payment(PaymentId.generate(), orderId, Money.of(new java.math.BigDecimal("100.00"), "USD"), "CREDIT_CARD");
        when(paymentRepository.save(any())).thenReturn(savedPayment);

        Payment result = paymentService.processPayment(orderId, 100.00, "CREDIT_CARD", authenticatedCustomerId);

        assertThat(result.getOrderId()).isEqualTo(orderId);
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void shouldDenyCustomerProcessingPaymentForAnotherCustomersOrder() {
        String customerId = "customer-456";
        String orderId = "order-123";
        String authenticatedCustomerId = "customer-123";

        Order order = new Order(OrderId.from(orderId), customerId, List.of());
        when(orderRepository.findById(OrderId.from(orderId))).thenReturn(Optional.of(order));
        when(authorizationService.canProcessPayment(eq(authenticatedCustomerId), eq(customerId))).thenReturn(false);

        assertThatThrownBy(() -> paymentService.processPayment(orderId, 100.00, "CREDIT_CARD", authenticatedCustomerId))
                .isInstanceOf(AuthorizationException.class)
                .hasMessageContaining("Cannot process payment for order belonging to another customer");
    }

    @Test
    void shouldDenyDoublePaymentForSameOrder() {
        String customerId = "customer-123";
        String orderId = "order-123";
        String authenticatedCustomerId = "customer-123";

        Order order = new Order(OrderId.from(orderId), customerId, List.of());
        when(orderRepository.findById(OrderId.from(orderId))).thenReturn(Optional.of(order));
        when(paymentRepository.existsByOrderIdAndStatus(orderId, PaymentStatus.APPROVED)).thenReturn(true);

        assertThatThrownBy(() -> paymentService.processPayment(orderId, 100.00, "CREDIT_CARD", authenticatedCustomerId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Order already has an approved payment");
    }

    @Test
    void shouldReturnOrderNotFoundForNonExistentOrder() {
        String orderId = "non-existent";
        String authenticatedCustomerId = "customer-123";

        when(orderRepository.findById(OrderId.from(orderId))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.processPayment(orderId, 100.00, "CREDIT_CARD", authenticatedCustomerId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Order not found");
    }
}
