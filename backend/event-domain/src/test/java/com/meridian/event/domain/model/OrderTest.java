package com.meridian.event.domain.model;

import com.meridian.event.domain.exception.DomainException;
import com.meridian.event.domain.model.valueobjects.Money;
import com.meridian.event.domain.model.valueobjects.OrderId;
import com.meridian.event.domain.model.valueobjects.Sku;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    @Test
    void shouldCreateOrderWithValidLines() {
        OrderId orderId = OrderId.generate();
        List<OrderLine> lines = List.of(
                new OrderLine(Sku.of("SKU-1"), 2, Money.of(new BigDecimal("10.00"), "USD")),
                new OrderLine(Sku.of("SKU-2"), 1, Money.of(new BigDecimal("25.00"), "USD"))
        );

        Order order = new Order(orderId, "customer-123", lines);

        assertThat(order.getId()).isEqualTo(orderId);
        assertThat(order.getCustomerId()).isEqualTo("customer-123");
        assertThat(order.getLines()).hasSize(2);
        assertThat(order.getTotal().value()).isEqualByComparingTo("45.00");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(order.getVersion()).isEqualTo(0L);
        assertThat(order.getCreatedAt()).isNotNull();
        assertThat(order.getUpdatedAt()).isNotNull();
    }

    @Test
    void shouldConfirmOrder() {
        Order order = new Order(OrderId.generate(), "customer-123", List.of(
                new OrderLine(Sku.of("SKU-1"), 1, Money.of(new BigDecimal("10.00"), "USD"))
        ));

        order.confirm();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getVersion()).isEqualTo(1L);
        assertThat(order.getUpdatedAt()).isAfter(order.getCreatedAt());
    }

    @Test
    void shouldFailToConfirmNonCreatedOrder() {
        Order order = new Order(OrderId.generate(), "customer-123", List.of(
                new OrderLine(Sku.of("SKU-1"), 1, Money.of(new BigDecimal("10.00"), "USD"))
        ));
        order.confirm();

        assertThatThrownBy(order::confirm)
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Cannot confirm order in status: CONFIRMED");
    }

    @Test
    void shouldCancelOrder() {
        Order order = new Order(OrderId.generate(), "customer-123", List.of(
                new OrderLine(Sku.of("SKU-1"), 1, Money.of(new BigDecimal("10.00"), "USD"))
        ));

        order.cancel("Customer requested");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getVersion()).isEqualTo(1L);
    }

    @Test
    void shouldFailToCancelCompletedOrder() {
        Order order = new Order(OrderId.generate(), "customer-123", List.of(
                new OrderLine(Sku.of("SKU-1"), 1, Money.of(new BigDecimal("10.00"), "USD"))
        ));
        order.confirm();
        // Simulate completion by setting status directly (would be done by saga)
        order.setStatus(OrderStatus.COMPLETED);

        assertThatThrownBy(() -> order.cancel("Cannot cancel"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Cannot cancel order in status: COMPLETED");
    }

    @Test
    void shouldAddLineToCreatedOrder() {
        Order order = new Order(OrderId.generate(), "customer-123", List.of(
                new OrderLine(Sku.of("SKU-1"), 1, Money.of(new BigDecimal("10.00"), "USD"))
        ));

        order.addLine(Sku.of("SKU-2"), 2, Money.of(new BigDecimal("15.00"), "USD"));

        assertThat(order.getLines()).hasSize(2);
        assertThat(order.getTotal().value()).isEqualByComparingTo("40.00");
        assertThat(order.getVersion()).isEqualTo(1L);
    }

    @Test
    void shouldFailToAddLineToConfirmedOrder() {
        Order order = new Order(OrderId.generate(), "customer-123", List.of(
                new OrderLine(Sku.of("SKU-1"), 1, Money.of(new BigDecimal("10.00"), "USD"))
        ));
        order.confirm();

        assertThatThrownBy(() -> order.addLine(Sku.of("SKU-2"), 1, Money.of(new BigDecimal("10.00"), "USD")))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Cannot modify order in status: CONFIRMED");
    }

    @Test
    void shouldReturnUnmodifiableLines() {
        Order order = new Order(OrderId.generate(), "customer-123", List.of(
                new OrderLine(Sku.of("SKU-1"), 1, Money.of(new BigDecimal("10.00"), "USD"))
        ));

        assertThatThrownBy(() -> order.getLines().add(new OrderLine(Sku.of("SKU-2"), 1, Money.of(new BigDecimal("10.00"), "USD"))))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @MethodSource("invalidLineArguments")
    void shouldRejectOrderWithInvalidLines(String customerId, List<OrderLine> lines, String expectedError) {
        Order order = new Order(OrderId.generate(), customerId, lines);

        assertThat(validator.validate(order).isValid()).isFalse();
        assertThat(validator.validate(order).errorMessage()).contains(expectedError);
    }

    static Stream<Arguments> invalidLineArguments() {
        return Stream.of(
                Arguments.of("customer-123", List.of(), "at least one line item"),
                Arguments.of("customer-123", List.of(new OrderLine(Sku.of("SKU-1"), 0, Money.of(new BigDecimal("10.00"), "USD"))), "Invalid quantity"),
                Arguments.of("customer-123", List.of(new OrderLine(Sku.of("SKU-1"), -1, Money.of(new BigDecimal("10.00"), "USD"))), "Invalid quantity"),
                Arguments.of("customer-123", List.of(new OrderLine(Sku.of("SKU-1"), 1, Money.of(new BigDecimal("-5.00"), "USD"))), "Unit price cannot be negative")
        );
    }

    // Test helper for controller tests
    static Order createMockOrder() {
        Order order = new Order(OrderId.from("order-123"), "customer-123", List.of(
                new OrderLine(Sku.of("SKU-1"), 1, Money.of(new BigDecimal("100.00"), "USD"))
        ));
        order.setStatus(OrderStatus.CONFIRMED);
        return order;
    }

    private final OrderValidator validator = new OrderValidator();
}