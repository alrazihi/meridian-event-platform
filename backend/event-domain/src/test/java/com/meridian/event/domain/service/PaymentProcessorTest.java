package com.meridian.event.domain.service;

import com.meridian.event.domain.model.Payment;
import com.meridian.event.domain.model.valueobjects.Money;
import com.meridian.event.domain.model.valueobjects.PaymentId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentProcessorTest {

    private final PaymentProcessor processor = new PaymentProcessor();

    @Test
    void shouldProcessPaymentSuccessfully() {
        Payment payment = new Payment(
                PaymentId.generate(),
                "order-123",
                Money.of(new BigDecimal("100.00"), "USD"),
                "CREDIT_CARD"
        );

        PaymentProcessor.ProcessingResult result = processor.process(payment, payment.getAmount());

        assertThat(result.success()).isTrue();
    }

    @Test
    void shouldFailWhenAmountMismatch() {
        Payment payment = new Payment(
                PaymentId.generate(),
                "order-123",
                Money.of(new BigDecimal("100.00"), "USD"),
                "CREDIT_CARD"
        );

        PaymentProcessor.ProcessingResult result = processor.process(payment, Money.of(new BigDecimal("50.00"), "USD"));

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("amount mismatch");
    }
}
