package com.meridian.event.domain.model;

import com.meridian.event.domain.exception.DomainException;
import com.meridian.event.domain.model.valueobjects.Money;
import com.meridian.event.domain.model.valueobjects.PaymentId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentTest {

    @Test
    void shouldCreatePaymentWithValidData() {
        PaymentId paymentId = PaymentId.generate();
        Money amount = Money.of(new BigDecimal("100.00"), "USD");

        Payment payment = new Payment(paymentId, "order-123", amount, "CREDIT_CARD");

        assertThat(payment.getId()).isEqualTo(paymentId);
        assertThat(payment.getOrderId()).isEqualTo("order-123");
        assertThat(payment.getAmount()).isEqualTo(amount);
        assertThat(payment.getPaymentMethod()).isEqualTo("CREDIT_CARD");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getVersion()).isEqualTo(0L);
        assertThat(payment.getCreatedAt()).isNotNull();
        assertThat(payment.getUpdatedAt()).isNotNull();
    }

    @Test
    void shouldApprovePayment() {
        Payment payment = new Payment(PaymentId.generate(), "order-123", Money.of(new BigDecimal("100.00"), "USD"), "CREDIT_CARD");

        payment.approve();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(payment.getVersion()).isEqualTo(1L);
        assertThat(payment.getUpdatedAt()).isAfter(payment.getCreatedAt());
    }

    @Test
    void shouldFailToApproveNonPendingPayment() {
        Payment payment = new Payment(PaymentId.generate(), "order-123", Money.of(new BigDecimal("100.00"), "USD"), "CREDIT_CARD");
        payment.approve();

        assertThatThrownBy(payment::approve)
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Cannot approve payment in status: APPROVED");
    }

    @Test
    void shouldRejectPayment() {
        Payment payment = new Payment(PaymentId.generate(), "order-123", Money.of(new BigDecimal("100.00"), "USD"), "CREDIT_CARD");

        payment.reject("Insufficient funds");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REJECTED);
        assertThat(payment.getVersion()).isEqualTo(1L);
    }

    @Test
    void shouldFailToRejectNonPendingPayment() {
        Payment payment = new Payment(PaymentId.generate(), "order-123", Money.of(new BigDecimal("100.00"), "USD"), "CREDIT_CARD");
        payment.approve();

        assertThatThrownBy(() -> payment.reject("Cannot reject"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Cannot reject payment in status: APPROVED");
    }

    @ParameterizedTest
    @MethodSource("invalidPaymentArguments")
    void shouldFailToCreatePaymentWithInvalidData(String orderId, Money amount, String paymentMethod, String expectedError) {
        PaymentId paymentId = PaymentId.generate();

        assertThatThrownBy(() -> new Payment(paymentId, orderId, amount, paymentMethod))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining(expectedError);
    }

    static Stream<Arguments> invalidPaymentArguments() {
        return Stream.of(
                Arguments.of(null, Money.of(new BigDecimal("100.00"), "USD"), "CREDIT_CARD", "orderId cannot be null"),
                Arguments.of("order-123", null, "CREDIT_CARD", "amount cannot be null"),
                Arguments.of("order-123", Money.of(new BigDecimal("100.00"), "USD"), null, "paymentMethod cannot be null")
        );
    }
}