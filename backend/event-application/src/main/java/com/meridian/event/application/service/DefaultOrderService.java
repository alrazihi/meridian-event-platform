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
import com.meridian.event.infrastructure.observability.BusinessMetrics;
import com.meridian.event.infrastructure.observability.CorrelationIdContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class DefaultOrderService implements PlaceOrderUseCase, QueryOrderStatusUseCase {

    private static final Logger log = LoggerFactory.getLogger("WORKFLOW");

    private final OrderRepository orderRepository;
    private final EventPublisher eventPublisher;
    private final NotificationService notificationService;
    private final ProcessPaymentUseCase processPaymentUseCase;
    private final ReserveInventoryUseCase reserveInventoryUseCase;
    private final OrderValidator orderValidator;
    private final BusinessMetrics businessMetrics;

    public DefaultOrderService(
            OrderRepository orderRepository,
            EventPublisher eventPublisher,
            NotificationService notificationService,
            ProcessPaymentUseCase processPaymentUseCase,
            ReserveInventoryUseCase reserveInventoryUseCase,
            OrderValidator orderValidator,
            BusinessMetrics businessMetrics) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
        this.notificationService = notificationService;
        this.processPaymentUseCase = processPaymentUseCase;
        this.reserveInventoryUseCase = reserveInventoryUseCase;
        this.orderValidator = orderValidator;
        this.businessMetrics = businessMetrics;
    }

    @Override
    @Transactional
    public Order placeOrder(String customerId, List<OrderLineInput> lines, String authenticatedCustomerId) {
        String correlationId = CorrelationIdContext.getCorrelationId();
        
        // Regular users can only place orders for themselves; admins can place for others
        if (!isAdmin() && !customerId.equals(authenticatedCustomerId)) {
            log.warn("Order placement authorization denied customerId={} authenticatedCustomerId={} correlationId={}",
                    customerId, authenticatedCustomerId, correlationId);
            throw new AccessDeniedException("Cannot place order for another customer");
        }

        OrderId orderId = OrderId.generate();
        List<OrderLine> orderLines = lines.stream()
                .map(line -> new OrderLine(Sku.of(line.getSku()), line.getQuantity(), Money.of(new java.math.BigDecimal(line.getUnitPrice()), "USD")))
                .toList();

        Order order = new Order(orderId, customerId, orderLines);

        OrderValidator.ValidationResult validationResult = orderValidator.validate(order);
        if (!validationResult.isValid()) {
            log.warn("Order validation failed customerId={} error={} correlationId={}",
                    customerId, validationResult.errorMessage(), correlationId);
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

        businessMetrics.incrementOrdersPlaced();
        businessMetrics.incrementEventsPublished();

        notificationService.notifyOrderConfirmed(savedOrder.getId().value(), savedOrder.getCustomerId());

        log.info("Order placed orderId={} customerId={} total={} correlationId={}",
                savedOrder.getId().value(), customerId, savedOrder.getTotal().value(), correlationId);

        return savedOrder;
    }

    @Override
    @Transactional(readOnly = true)
    public Order getOrderStatus(String orderId, String authenticatedCustomerId) {
        Order order = orderRepository.findById(OrderId.from(orderId))
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        // Resource-level authorization: users can only access their own orders
        if (!isAdmin() && !order.getCustomerId().equals(authenticatedCustomerId)) {
            throw new AccessDeniedException("Access denied to order: " + orderId);
        }

        return order;
    }

    private boolean isAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null) {
            return authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        }
        return false;
    }
}

