package com.meridian.event.application.service;

import com.meridian.event.application.port.inbound.ProcessPaymentUseCase;
import com.meridian.event.application.port.outbound.PaymentRepository;
import com.meridian.event.domain.model.Payment;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@Import(TestConfig.class)
@Transactional
class DefaultPaymentServiceIntegrationTest {

    @Autowired
    private ProcessPaymentUseCase processPaymentUseCase;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private com.meridian.event.application.port.inbound.PlaceOrderUseCase placeOrderUseCase;

    @Autowired
    private com.meridian.event.application.port.inbound.OrderLineInput;

    @Test
    void shouldProcessPaymentSuccessfully() {
        // First place an order
        Order order = placeOrderUseCase.placeOrder("customer-123", List.of(new OrderLineInput("SKU-1", 1, 100.00)));

        // Process payment
        Payment payment = processPaymentUseCase.processPayment(order.getId().value(), 100.00, "CREDIT_CARD");

        assertThat(payment.getId()).isNotNull();
        assertThat(payment.getOrderId()).isEqualTo(order.getId().value());
        assertThat(payment.getAmount().value()).isEqualByComparingTo("100.00");
        assertThat(payment.getPaymentMethod()).isEqualTo("CREDIT_CARD");
        assertThat(payment.getStatus()).isEqualTo(com.meridian.event.domain.model.PaymentStatus.APPROVED);

        // Verify persistence
        Payment persisted = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(com.meridian.event.domain.model.PaymentStatus.APPROVED);
    }

    @Test
    void shouldFailWhenAmountMismatch() {
        Order order = placeOrderUseCase.placeOrder("customer-123", List.of(new OrderLineInput("SKU-1", 1, 100.00)));

        assertThatThrownBy(() -> processPaymentUseCase.processPayment(order.getId().value(), 50.00, "CREDIT_CARD"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("amount mismatch");
    }

    @Test
    void shouldFailWhenOrderNotFound() {
        assertThatThrownBy(() -> processPaymentUseCase.processPayment("non-existent-order", 100.00, "CREDIT_CARD"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Order not found");
    }
}