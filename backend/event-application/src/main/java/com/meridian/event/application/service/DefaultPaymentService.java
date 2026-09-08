package com.meridian.event.application.service;

import com.meridian.event.application.port.inbound.ProcessPaymentUseCase;
import com.meridian.event.application.port.outbound.EventPublisher;
import com.meridian.event.application.port.outbound.NotificationService;
import com.meridian.event.application.port.outbound.OrderRepository;
import com.meridian.event.application.port.outbound.PaymentRepository;
import com.meridian.event.domain.model.Order;
import com.meridian.event.domain.model.valueobjects.Money;
import com.meridian.event.domain.model.valueobjects.OrderId;
import com.meridian.event.domain.model.valueobjects.PaymentId;
import com.meridian.event.domain.model.PaymentProcessedEvent;
import com.meridian.event.domain.service.PaymentProcessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultPaymentService implements ProcessPaymentUseCase {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final EventPublisher eventPublisher;
    private final NotificationService notificationService;
    private final PaymentProcessor paymentProcessor;

    public DefaultPaymentService(
            OrderRepository orderRepository,
            PaymentRepository paymentRepository,
            EventPublisher eventPublisher,
            NotificationService notificationService,
            PaymentProcessor paymentProcessor) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.eventPublisher = eventPublisher;
        this.notificationService = notificationService;
        this.paymentProcessor = paymentProcessor;
    }

    @Override
    @Transactional
    public com.meridian.event.domain.model.Payment processPayment(String orderId, double amount, String paymentMethod, String authenticatedCustomerId) {
        OrderId oid = OrderId.from(orderId);
        Order order = orderRepository.findById(oid)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        // Resource-level authorization: verify the order belongs to the authenticated customer
        if (!order.getCustomerId().equals(authenticatedCustomerId)) {
            throw new AccessDeniedException("Cannot process payment for order belonging to another customer");
        }

        // Prevent double payment
        if (paymentRepository.existsByOrderIdAndStatus(orderId, com.meridian.event.domain.model.PaymentStatus.APPROVED)) {
            throw new IllegalStateException("Order already has an approved payment");
        }

        PaymentId paymentId = PaymentId.generate();
        com.meridian.event.domain.model.Payment payment = new com.meridian.event.domain.model.Payment(
                paymentId, orderId, Money.of(new java.math.BigDecimal(amount), "USD"), paymentMethod
        );

        PaymentProcessor.ProcessingResult result = paymentProcessor.process(payment, payment.getAmount());
        if (!result.success()) {
            throw new IllegalStateException(result.errorMessage());
        }

        payment.approve();

        Payment savedPayment = paymentRepository.save(payment);

        PaymentProcessedEvent event = new PaymentProcessedEvent(
                paymentId.value(),
                orderId,
                payment.getAmount().value().toString(),
                "APPROVED",
                paymentId.value()
        );
        eventPublisher.publish(event);

        return savedPayment;
    }
}


