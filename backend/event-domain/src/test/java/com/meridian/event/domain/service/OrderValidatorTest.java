package com.meridian.event.domain.service;

import com.meridian.event.domain.model.Order;
import com.meridian.event.domain.model.OrderLine;
import com.meridian.event.domain.model.valueobjects.Money;
import com.meridian.event.domain.model.valueobjects.OrderId;
import com.meridian.event.domain.model.valueobjects.Sku;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

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
}
