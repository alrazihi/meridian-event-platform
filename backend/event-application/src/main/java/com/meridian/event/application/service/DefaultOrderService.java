package com.meridian.event.application.service;

import com.meridian.event.application.port.inbound.OrderLineInput;
import com.meridian.event.application.port.inbound.PlaceOrderUseCase;
import com.meridian.event.application.port.inbound.ProcessPaymentUseCase;
import com.meridian.event.application.port.inbound.QueryOrderStatusUseCase;
import com.meridian.event.application.port.inbound.ReserveInventoryUseCase;
import com.meridian.event.application.port.outbound.EventPublisher;
import com.meridian.event.application.port.outbound.NotificationService;
import com.meridian.event.application.port.outbound.OrderRepository;
import com.meridian.event.domain.model.Order;
import com.meridian.event.domain.model.OrderLine;
import com.meridian.event.domain.model.valueobjects.Money;
import com.meridian.event.domain.model.valueobjects.OrderId;
import com.meridian.event.domain.model.valueobjects.Sku;
import com.meridian.event.domain.model.OrderConfirmedEvent;
import com.meridian.event.domain.service.OrderValidator;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class DefaultOrderService implements PlaceOrderUseCase, QueryOrderStatusUseCase {

    private final OrderRepository orderRepository;
    private final EventPublisher eventPublisher;
    private final NotificationService notificationService;
    private final ProcessPaymentUseCase processPaymentUseCase;
    private final ReserveInventoryUseCase reserveInventoryUseCase;
    private final OrderValidator orderValidator;

    public DefaultOrderService(
            OrderRepository orderRepository,
            EventPublisher eventPublisher,
            NotificationService notificationService,
            ProcessPaymentUseCase processPaymentUseCase,
            ReserveInventoryUseCase reserveInventoryUseCase,
            OrderValidator orderValidator) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
        this.notificationService = notificationService;
        this.processPaymentUseCase = processPaymentUseCase;
        this.reserveInventoryUseCase = reserveInventoryUseCase;
        this.orderValidator = orderValidator;
    }

    @Override
    @Transactional
    public Order placeOrder(String customerId, List<OrderLineInput> lines, String authenticatedCustomerId) {
        // Regular users can only place orders for themselves; admins can place for others
        if (!isAdmin(authenticatedCustomerId) && !customerId.equals(authenticatedCustomerId)) {
            throw new AccessDeniedException("Cannot place order for another customer");
        }

        OrderId orderId = OrderId.generate();
        List<OrderLine> orderLines = lines.stream()
                .map(line -> new OrderLine(Sku.of(line.getSku()), line.getQuantity(), Money.of(new java.math.BigDecimal(line.getUnitPrice()), "USD")))
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
    @Transactional(readOnly = true)
    public Order getOrderStatus(String orderId, String authenticatedCustomerId) {
        Order order = orderRepository.findById(OrderId.from(orderId))
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        // Resource-level authorization: users can only access their own orders
        if (!isAdmin(authenticatedCustomerId) && !order.getCustomerId().equals(authenticatedCustomerId)) {
            throw new AccessDeniedException("Access denied to order: " + orderId);
        }

        return order;
    }

    private boolean isAdmin(String customerId) {
        // In a real implementation, this would check the JWT roles claim
        // For now, we'll check if there's an ADMIN role in the authentication context
        // This is a placeholder - actual implementation would use SecurityContext
        return false; // Will be overridden by controller-level @PreAuthorize
    }
}

