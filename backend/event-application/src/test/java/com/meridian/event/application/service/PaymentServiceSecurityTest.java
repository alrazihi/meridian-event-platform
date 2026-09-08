package com.meridian.event.application.service;

import com.meridian.event.application.port.inbound.ProcessPaymentUseCase;
import com.meridian.event.application.port.outbound.EventPublisher;
import com.meridian.event.application.port.outbound.NotificationService;
import com.meridian.event.application.port.outbound.OrderRepository;
import com.meridian.event.application.port.outbound.PaymentRepository;
import com.meridian.event.domain.model.Order;
import com.meridian.event.domain.model.Payment;
import com.meridian.event.domain.model.PaymentStatus;
import com.meridian.event.domain.model.valueobjects.Money;
import com.meridian.event.domain.model.valueobjects.OrderId;
import com.meridian.event.domain.model.valueobjects.PaymentId;
import com.meridian.event.domain.service.PaymentProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
    private PaymentProcessor paymentProcessor;

    private DefaultPaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new DefaultPaymentService(
                orderRepository,
                paymentRepository,
                eventPublisher,
                notificationService,
                paymentProcessor
        );

        when(paymentProcessor.process(any(), any())).thenReturn(new PaymentProcessor.ProcessingResult(true, null));
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
        savedPayment.approve();
        when(paymentRepository.save(any())).thenReturn(savedPayment);

        Payment result = paymentService.processPayment(orderId, 100.00, "CREDIT_CARD", authenticatedCustomerId);

        assertThat(result.getOrderId()).isEqualTo(orderId);
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    void shouldDenyCustomerProcessingPaymentForAnotherCustomersOrder() {
        String customerId = "customer-456";
        String orderId = "order-123";
        String authenticatedCustomerId = "customer-123";

        Order order = new Order(OrderId.from(orderId), customerId, List.of());
        when(orderRepository.findById(OrderId.from(orderId))).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> paymentService.processPayment(orderId, 100.00, "CREDIT_CARD", authenticatedCustomerId))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
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
                .hasMessageContaining("Payment already processed");
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

    @Test
    void shouldRejectAmountMismatch() {
        String customerId = "customer-123";
        String orderId = "order-123";
        String authenticatedCustomerId = "customer-123";

        Order order = new Order(OrderId.from(orderId), customerId, List.of());
        when(orderRepository.findById(OrderId.from(orderId))).thenReturn(Optional.of(order));
        when(paymentRepository.existsByOrderIdAndStatus(orderId, PaymentStatus.APPROVED)).thenReturn(false);
        when(paymentProcessor.process(any(), any())).thenReturn(new PaymentProcessor.ProcessingResult(false, "amount mismatch"));

        assertThatThrownBy(() -> paymentService.processPayment(orderId, 50.00, "CREDIT_CARD", authenticatedCustomerId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("amount mismatch");
    }
}