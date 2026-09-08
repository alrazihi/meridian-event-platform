package com.meridian.event.application.service;

import com.meridian.event.application.port.inbound.OrderLineInput;
import com.meridian.event.application.port.inbound.PlaceOrderUseCase;
import com.meridian.event.application.port.inbound.ProcessPaymentUseCase;
import com.meridian.event.application.port.inbound.ReserveInventoryUseCase;
import com.meridian.event.application.port.outbound.EventPublisher;
import com.meridian.event.application.port.outbound.NotificationService;
import com.meridian.event.application.port.outbound.OrderRepository;
import com.meridian.event.domain.model.Order;
import com.meridian.event.domain.model.OrderLine;
import com.meridian.event.domain.model.valueobjects.Money;
import com.meridian.event.domain.model.valueobjects.OrderId;
import com.meridian.event.domain.model.valueobjects.Sku;
import com.meridian.event.domain.service.OrderValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DefaultOrderService implements PlaceOrderUseCase {

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
    public Order placeOrder(String customerId, List<OrderLineInput> lines) {
        OrderId orderId = OrderId.generate();
        List<OrderLine> orderLines = lines.stream()
                .map(line -> new OrderLine(Sku.of(line.getSku()), line.getQuantity(), Money.of(new java.math.BigDecimal(line.getUnitPrice()), "USD")))
                .toList();

        Order order = new Order(orderId, customerId, orderLines);

        OrderValidator.ValidationResult validationResult = orderValidator.validate(order);
        if (!validationResult.valid()) {
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

        return savedOrder;
    }
}
