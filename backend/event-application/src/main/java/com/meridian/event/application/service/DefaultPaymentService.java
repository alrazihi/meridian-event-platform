package com.meridian.event.application.service;

import com.meridian.event.application.port.inbound.ProcessPaymentUseCase;
import com.meridian.event.application.port.inbound.ReserveInventoryUseCase;
import com.meridian.event.application.port.outbound.EventPublisher;
import com.meridian.event.application.port.outbound.NotificationService;
import com.meridian.event.application.port.outbound.OrderRepository;
import com.meridian.event.domain.model.DomainEvent;
import com.meridian.event.domain.model.Order;
import com.meridian.event.domain.model.valueobjects.Money;
import com.meridian.event.domain.model.valueobjects.OrderId;
import com.meridian.event.domain.model.valueobjects.PaymentId;
import com.meridian.event.domain.model.valueobjects.Sku;
import com.meridian.event.domain.service.PaymentProcessor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultPaymentService implements ProcessPaymentUseCase {

    private final OrderRepository orderRepository;
    private final EventPublisher eventPublisher;
    private final NotificationService notificationService;
    private final PaymentProcessor paymentProcessor;

    public DefaultPaymentService(
            OrderRepository orderRepository,
            EventPublisher eventPublisher,
            NotificationService notificationService,
            PaymentProcessor paymentProcessor) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
        this.notificationService = notificationService;
        this.paymentProcessor = paymentProcessor;
    }

    @Override
    @Transactional
    public com.meridian.event.domain.model.Payment processPayment(String orderId, double amount, String paymentMethod) {
        OrderId oid = OrderId.from(orderId);
        Order order = orderRepository.findById(oid)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        PaymentId paymentId = PaymentId.generate();
        com.meridian.event.domain.model.Payment payment = new com.meridian.event.domain.model.Payment(
                paymentId, orderId, Money.of(new java.math.BigDecimal(amount), "USD"), paymentMethod
        );

        PaymentProcessor.ProcessingResult result = paymentProcessor.process(payment, payment.getAmount());
        if (!result.success()) {
            throw new IllegalStateException(result.errorMessage());
        }

        payment.approve();

        PaymentProcessedEvent event = new PaymentProcessedEvent(
                paymentId.value(),
                orderId,
                payment.getAmount().value().toString(),
                "APPROVED",
                paymentId.value()
        );
        eventPublisher.publish(event);

        return payment;
    }
}
