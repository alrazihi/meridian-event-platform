package com.meridian.event.application.service;

import com.meridian.event.application.port.inbound.OrderLineInput;
import com.meridian.event.application.port.inbound.PlaceOrderUseCase;
import com.meridian.event.application.port.inbound.QueryOrderStatusUseCase;
import com.meridian.event.application.port.outbound.AuthorizationService;
import com.meridian.event.application.port.outbound.EventPublisher;
import com.meridian.event.application.port.outbound.NotificationService;
import com.meridian.event.application.port.outbound.OrderRepository;
import com.meridian.event.domain.exception.AuthorizationException;
import com.meridian.event.domain.model.Order;
import com.meridian.event.domain.model.OrderLine;
import com.meridian.event.domain.model.valueobjects.Money;
import com.meridian.event.domain.model.valueobjects.OrderId;
import com.meridian.event.domain.model.valueobjects.Sku;
import com.meridian.event.domain.service.OrderValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceSecurityTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private NotificationService notificationService;

    @Mock
    private OrderValidator orderValidator;

    @Mock
    private AuthorizationService authorizationService;

    private DefaultOrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new DefaultOrderService(
                orderRepository,
                eventPublisher,
                notificationService,
                orderValidator,
                authorizationService
        );

        when(orderValidator.validate(any())).thenReturn(new OrderValidator.ValidationResult(true, null));
        when(authorizationService.canAccessOrder(any(), any())).thenReturn(true);
    }

    @Test
    void shouldAllowCustomerToPlaceOrderForSelf() {
        String customerId = "customer-123";
        String authenticatedCustomerId = "customer-123";
        OrderLineInput line = new OrderLineInput("SKU-1", 1, 10.00);

        Order savedOrder = new Order(OrderId.generate(), customerId, List.of(
                new OrderLine(Sku.of("SKU-1"), 1, Money.of(new BigDecimal("10.00"), "USD"))
        ));
        when(orderRepository.save(any())).thenReturn(savedOrder);

        Order result = orderService.placeOrder(customerId, List.of(line), authenticatedCustomerId);

        assertThat(result.getCustomerId()).isEqualTo(customerId);
    }

    @Test
    void shouldDenyCustomerPlacingOrderForAnotherCustomer() {
        String customerId = "customer-456";
        String authenticatedCustomerId = "customer-123";
        OrderLineInput line = new OrderLineInput("SKU-1", 1, 10.00);

        when(authorizationService.canAccessOrder(eq(authenticatedCustomerId), eq(customerId))).thenReturn(false);

        assertThatThrownBy(() -> orderService.placeOrder(customerId, List.of(line), authenticatedCustomerId))
                .isInstanceOf(AuthorizationException.class)
                .hasMessageContaining("Cannot place order for another customer");
    }

    @Test
    void shouldAllowCustomerToViewOwnOrder() {
        String customerId = "customer-123";
        String orderId = "order-123";
        String authenticatedCustomerId = "customer-123";

        Order order = new Order(OrderId.from(orderId), customerId, List.of());
        when(orderRepository.findById(OrderId.from(orderId))).thenReturn(Optional.of(order));

        Order result = orderService.getOrderStatus(orderId, authenticatedCustomerId);

        assertThat(result.getId().value()).isEqualTo(orderId);
        assertThat(result.getCustomerId()).isEqualTo(customerId);
    }

    @Test
    void shouldDenyCustomerViewingAnotherCustomersOrder() {
        String customerId = "customer-456";
        String orderId = "order-123";
        String authenticatedCustomerId = "customer-123";

        Order order = new Order(OrderId.from(orderId), customerId, List.of());
        when(orderRepository.findById(OrderId.from(orderId))).thenReturn(Optional.of(order));
        when(authorizationService.canAccessOrder(eq(authenticatedCustomerId), eq(customerId))).thenReturn(false);

        assertThatThrownBy(() -> orderService.getOrderStatus(orderId, authenticatedCustomerId))
                .isInstanceOf(AuthorizationException.class)
                .hasMessageContaining("Access denied");
    }

    @Test
    void shouldReturnOrderNotFoundForNonExistentOrder() {
        String orderId = "non-existent";
        String authenticatedCustomerId = "customer-123";

        when(orderRepository.findById(OrderId.from(orderId))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrderStatus(orderId, authenticatedCustomerId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Order not found");
    }
}
