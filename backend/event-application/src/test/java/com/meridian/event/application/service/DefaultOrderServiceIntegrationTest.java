package com.meridian.event.application.service;

import com.meridian.event.application.port.inbound.OrderLineInput;
import com.meridian.event.application.port.inbound.PlaceOrderUseCase;
import com.meridian.event.application.port.inbound.QueryOrderStatusUseCase;
import com.meridian.event.application.port.outbound.EventPublisher;
import com.meridian.event.application.port.outbound.NotificationService;
import com.meridian.event.application.port.outbound.OrderRepository;
import com.meridian.event.domain.model.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@Import(TestConfig.class)
@Transactional
class DefaultOrderServiceIntegrationTest {

    @Autowired
    private PlaceOrderUseCase placeOrderUseCase;

    @Autowired
    private QueryOrderStatusUseCase queryOrderStatusUseCase;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private EventPublisher eventPublisher;

    @Autowired
    private NotificationService notificationService;

    @Test
    void shouldPlaceOrderAndPersist() {
        List<OrderLineInput> lines = List.of(
                new OrderLineInput("SKU-1", 2, 10.00),
                new OrderLineInput("SKU-2", 1, 25.00)
        );

        Order order = placeOrderUseCase.placeOrder("customer-123", lines, "customer-123");

        assertThat(order.getId()).isNotNull();
        assertThat(order.getCustomerId()).isEqualTo("customer-123");
        assertThat(order.getLines()).hasSize(2);
        assertThat(order.getTotal().value()).isEqualByComparingTo("45.00");
        assertThat(order.getStatus()).isEqualTo(com.meridian.event.domain.model.OrderStatus.CREATED);

        Order persisted = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(persisted.getId()).isEqualTo(order.getId());
        assertThat(persisted.getTotal().value()).isEqualByComparingTo("45.00");
    }

    @Test
    void shouldRejectOrderWithInvalidLines() {
        List<OrderLineInput> lines = List.of(
                new OrderLineInput("SKU-1", 0, 10.00)
        );

        assertThatThrownBy(() -> placeOrderUseCase.placeOrder("customer-123", lines, "customer-123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid quantity");
    }

    @Test
    void shouldQueryOrderStatus() {
        List<OrderLineInput> lines = List.of(new OrderLineInput("SKU-1", 1, 10.00));
        Order placed = placeOrderUseCase.placeOrder("customer-123", lines, "customer-123");

        Order queried = queryOrderStatusUseCase.getOrderStatus(placed.getId().value(), "customer-123");

        assertThat(queried.getId()).isEqualTo(placed.getId());
        assertThat(queried.getCustomerId()).isEqualTo("customer-123");
        assertThat(queried.getTotal().value()).isEqualByComparingTo("10.00");
    }

    @Test
    void shouldFailToQueryNonExistentOrder() {
        String nonExistentId = UUID.randomUUID().toString();

        assertThatThrownBy(() -> queryOrderStatusUseCase.getOrderStatus(nonExistentId, "customer-123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Order not found");
    }
}