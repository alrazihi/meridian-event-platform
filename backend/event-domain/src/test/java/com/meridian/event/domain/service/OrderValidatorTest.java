package com.meridian.event.domain.service;

import com.meridian.event.domain.model.Order;
import com.meridian.event.domain.model.OrderLine;
import com.meridian.event.domain.model.valueobjects.Money;
import com.meridian.event.domain.model.valueobjects.OrderId;
import com.meridian.event.domain.model.valueobjects.Sku;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class OrderValidatorTest {

    private final OrderValidator validator = new OrderValidator();

    @Test
    void shouldAcceptValidOrder() {
        Order order = new Order(
                OrderId.generate(),
                "customer-123",
                List.of(new OrderLine(Sku.of("SKU-1"), 2, Money.of(new BigDecimal("10.00"), "USD")))
        );

        OrderValidator.ValidationResult result = validator.validate(order);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void shouldRejectOrderWithNoLines() {
        Order order = new Order(OrderId.generate(), "customer-123", List.of());

        OrderValidator.ValidationResult result = validator.validate(order);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errorMessage()).contains("at least one line item");
    }

    @Test
    void shouldRejectOrderWithZeroQuantity() {
        Order order = new Order(
                OrderId.generate(),
                "customer-123",
                List.of(new OrderLine(Sku.of("SKU-1"), 0, Money.of(new BigDecimal("10.00"), "USD")))
        );

        OrderValidator.ValidationResult result = validator.validate(order);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errorMessage()).contains("Invalid quantity");
    }

    @Test
    void shouldRejectOrderWithNegativeQuantity() {
        Order order = new Order(
                OrderId.generate(),
                "customer-123",
                List.of(new OrderLine(Sku.of("SKU-1"), -1, Money.of(new BigDecimal("10.00"), "USD")))
        );

        OrderValidator.ValidationResult result = validator.validate(order);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errorMessage()).contains("Invalid quantity");
    }

    @Test
    void shouldRejectOrderWithNegativeUnitPrice() {
        Order order = new Order(
                OrderId.generate(),
                "customer-123",
                List.of(new OrderLine(Sku.of("SKU-1"), 1, Money.of(new BigDecimal("-5.00"), "USD")))
        );

        OrderValidator.ValidationResult result = validator.validate(order);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errorMessage()).contains("Unit price cannot be negative");
    }

    @Test
    void shouldRejectOrderWithZeroUnitPrice() {
        Order order = new Order(
                OrderId.generate(),
                "customer-123",
                List.of(new OrderLine(Sku.of("SKU-1"), 1, Money.of(new BigDecimal("0.00"), "USD")))
        );

        OrderValidator.ValidationResult result = validator.validate(order);

        assertThat(result.isValid()).isTrue(); // Zero price is valid (free item)
    }

    @Test
    void shouldAcceptOrderWithMultipleValidLines() {
        Order order = new Order(
                OrderId.generate(),
                "customer-123",
                List.of(
                        new OrderLine(Sku.of("SKU-1"), 2, Money.of(new BigDecimal("10.00"), "USD")),
                        new OrderLine(Sku.of("SKU-2"), 1, Money.of(new BigDecimal("25.00"), "USD")),
                        new OrderLine(Sku.of("SKU-3"), 3, Money.of(new BigDecimal("5.00"), "USD"))
                )
        );

        OrderValidator.ValidationResult result = validator.validate(order);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void shouldRejectOrderWithMixedValidAndInvalidLines() {
        Order order = new Order(
                OrderId.generate(),
                "customer-123",
                List.of(
                        new OrderLine(Sku.of("SKU-1"), 2, Money.of(new BigDecimal("10.00"), "USD")),
                        new OrderLine(Sku.of("SKU-2"), -1, Money.of(new BigDecimal("25.00"), "USD")) // invalid
                )
        );

        OrderValidator.ValidationResult result = validator.validate(order);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errorMessage()).contains("Invalid quantity");
    }

    @ParameterizedTest
    @MethodSource("validLineArguments")
    void shouldAcceptVariousValidLines(int quantity, BigDecimal unitPrice) {
        Order order = new Order(
                OrderId.generate(),
                "customer-123",
                List.of(new OrderLine(Sku.of("SKU-1"), quantity, Money.of(unitPrice, "USD")))
        );

        OrderValidator.ValidationResult result = validator.validate(order);

        assertThat(result.isValid()).isTrue();
    }

    static Stream<Arguments> validLineArguments() {
        return Stream.of(
                Arguments.of(1, new BigDecimal("0.01")),
                Arguments.of(1, new BigDecimal("1000000.00")),
                Arguments.of(1000, new BigDecimal("10.00")),
                Arguments.of(1, new BigDecimal("0.00")) // free item
        );
    }
}