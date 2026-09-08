package com.meridian.event.domain.service;

import com.meridian.event.domain.model.Payment;
import com.meridian.event.domain.model.valueobjects.Money;
import com.meridian.event.domain.model.valueobjects.PaymentId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.stream.Stream;

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

    @Test
    void shouldFailWhenAmountIsZero() {
        Payment payment = new Payment(
                PaymentId.generate(),
                "order-123",
                Money.of(new BigDecimal("0.00"), "USD"),
                "CREDIT_CARD"
        );

        PaymentProcessor.ProcessingResult result = processor.process(payment, payment.getAmount());

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("must be positive");
    }

    @Test
    void shouldFailWhenAmountIsNegative() {
        Payment payment = new Payment(
                PaymentId.generate(),
                "order-123",
                Money.of(new BigDecimal("-10.00"), "USD"),
                "CREDIT_CARD"
        );

        PaymentProcessor.ProcessingResult result = processor.process(payment, payment.getAmount());

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("must be positive");
    }

    @Test
    void shouldProcessDifferentPaymentMethods() {
        String[] methods = {"CREDIT_CARD", "DEBIT_CARD", "BANK_TRANSFER", "PAYPAL", "APPLE_PAY"};

        for (String method : methods) {
            Payment payment = new Payment(
                    PaymentId.generate(),
                    "order-123",
                    Money.of(new BigDecimal("100.00"), "USD"),
                    method
            );

            PaymentProcessor.ProcessingResult result = processor.process(payment, payment.getAmount());

            assertThat(result.success()).isTrue();
        }
    }

    @ParameterizedTest
    @MethodSource("amountMismatchArguments")
    void shouldFailOnAmountMismatch(BigDecimal paymentAmount, BigDecimal providedAmount) {
        Payment payment = new Payment(
                PaymentId.generate(),
                "order-123",
                Money.of(paymentAmount, "USD"),
                "CREDIT_CARD"
        );

        PaymentProcessor.ProcessingResult result = processor.process(payment, Money.of(providedAmount, "USD"));

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("amount mismatch");
    }

    static Stream<Arguments> amountMismatchArguments() {
        return Stream.of(
                Arguments.of(new BigDecimal("100.00"), new BigDecimal("50.00")),
                Arguments.of(new BigDecimal("100.00"), new BigDecimal("150.00")),
                Arguments.of(new BigDecimal("100.00"), new BigDecimal("0.00")),
                Arguments.of(new BigDecimal("100.00"), new BigDecimal("-10.00"))
        );
    }
}