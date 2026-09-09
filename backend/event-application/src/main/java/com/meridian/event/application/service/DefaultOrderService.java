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
import com.meridian.event.domain.model.OrderConfirmedEvent;
import com.meridian.event.domain.model.valueobjects.Money;
import com.meridian.event.domain.model.valueobjects.OrderId;
import com.meridian.event.domain.model.valueobjects.Sku;
import com.meridian.event.domain.service.OrderValidator;

import java.math.BigDecimal;
import java.util.List;

public class DefaultOrderService implements PlaceOrderUseCase, QueryOrderStatusUseCase {

    private final OrderRepository orderRepository;
    private final EventPublisher eventPublisher;
    private final NotificationService notificationService;
    private final OrderValidator orderValidator;
    private final AuthorizationService authorizationService;

    public DefaultOrderService(
            OrderRepository orderRepository,
            EventPublisher eventPublisher,
            NotificationService notificationService,
            OrderValidator orderValidator,
            AuthorizationService authorizationService) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
        this.notificationService = notificationService;
        this.orderValidator = orderValidator;
        this.authorizationService = authorizationService;
    }

    @Override
    public Order placeOrder(String customerId, List<OrderLineInput> lines, String authenticatedCustomerId) {
        if (!authorizationService.canAccessOrder(authenticatedCustomerId, customerId)) {
            throw new AuthorizationException("Cannot place order for another customer");
        }

        OrderId orderId = OrderId.generate();
        List<OrderLine> orderLines = lines.stream()
                .map(line -> new OrderLine(Sku.of(line.sku()), line.quantity(), Money.of(new BigDecimal(line.unitPrice()), "USD")))
                .toList();

        Order order = new Order(orderId, customerId, orderLines);

        OrderValidator.ValidationResult validationResult = orderValidator.validate(order);
        if (!validationResult.isValid()) {
            throw new IllegalArgumentException(validationResult.errorMessage());
        }

        Order savedOrder = orderRepository.save(order);

        OrderConfirmedEvent event = new OrderConfirmedEvent(
                savedOrder.getId().value(),
                savedOrder.getCustomerId(),
                savedOrder.getTotal().value().toString(),
                savedOrder.getId().value()
        );
        eventPublisher.publish(event);

        notificationService.notifyOrderConfirmed(savedOrder.getId().value(), savedOrder.getCustomerId());

        return savedOrder;
    }

    @Override
    public Order getOrderStatus(String orderId, String authenticatedCustomerId) {
        Order order = orderRepository.findById(OrderId.from(orderId))
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        if (!authorizationService.canAccessOrder(authenticatedCustomerId, order.getCustomerId())) {
            throw new AuthorizationException("Access denied to order: " + orderId);
        }

        return order;
    }
}
