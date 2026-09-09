package com.meridian.event.application.service;

import com.meridian.event.application.port.inbound.ProcessPaymentUseCase;
import com.meridian.event.application.port.inbound.CompletePaymentUseCase;
import com.meridian.event.application.port.outbound.AuthorizationService;
import com.meridian.event.application.port.outbound.ClientIpResolver;
import com.meridian.event.application.port.outbound.EventPublisher;
import com.meridian.event.application.port.outbound.NotificationService;
import com.meridian.event.application.port.outbound.OrderRepository;
import com.meridian.event.application.port.outbound.PaymentRepository;
import com.meridian.event.application.port.outbound.PaymentGateway;
import com.meridian.event.domain.exception.AuthorizationException;
import com.meridian.event.domain.model.Order;
import com.meridian.event.domain.model.Payment;
import com.meridian.event.domain.model.PaymentProcessedEvent;
import com.meridian.event.domain.model.PaymentStatus;
import com.meridian.event.domain.model.valueobjects.Money;
import com.meridian.event.domain.model.valueobjects.OrderId;
import com.meridian.event.domain.model.valueobjects.PaymentId;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class DefaultPaymentService implements ProcessPaymentUseCase, CompletePaymentUseCase {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final EventPublisher eventPublisher;
    private final NotificationService notificationService;
    private final PaymentGateway paymentGateway;
    private final AuthorizationService authorizationService;
    private final ClientIpResolver clientIpResolver;
    private final Executor taskExecutor;

    public DefaultPaymentService(
            OrderRepository orderRepository,
            PaymentRepository paymentRepository,
            EventPublisher eventPublisher,
            NotificationService notificationService,
            PaymentGateway paymentGateway,
            AuthorizationService authorizationService,
            ClientIpResolver clientIpResolver,
            Executor taskExecutor) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.eventPublisher = eventPublisher;
        this.notificationService = notificationService;
        this.paymentGateway = paymentGateway;
        this.authorizationService = authorizationService;
        this.clientIpResolver = clientIpResolver;
        this.taskExecutor = taskExecutor;
    }

    @Override
    public Payment processPayment(String orderId, double amount, String paymentMethod, String authenticatedCustomerId) {
        OrderId oid = OrderId.from(orderId);
        Order order = orderRepository.findById(oid)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        if (!authorizationService.canProcessPayment(authenticatedCustomerId, order.getCustomerId())) {
            throw new AuthorizationException("Cannot process payment for order belonging to another customer");
        }

        if (paymentRepository.existsByOrderIdAndStatus(orderId, PaymentStatus.APPROVED)) {
            throw new IllegalStateException("Order already has an approved payment");
        }

        PaymentId paymentId = PaymentId.generate();
        Payment payment = new Payment(
                paymentId, orderId, Money.of(new java.math.BigDecimal(amount), "USD"), paymentMethod
        );

        Payment pendingPayment = paymentRepository.save(payment);

        return pendingPayment;
    }

    @Override
    public void completePayment(String paymentId, String authenticatedCustomerId) {
        Payment payment = paymentRepository.findById(com.meridian.event.domain.model.valueobjects.PaymentId.from(paymentId))
                .orElseThrow(() -> new IllegalArgumentException("Payment not found"));

        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new IllegalStateException("Payment is not in PENDING state");
        }

        PaymentGateway.ProcessingResult result = paymentGateway.charge(payment, payment.getAmount());

        if (!result.success()) {
            payment.reject(result.errorMessage());
            paymentRepository.save(payment);
            return;
        }

        payment.approve();
        payment.setTransactionId(result.transactionId());
        Payment savedPayment = paymentRepository.save(payment);

        PaymentProcessedEvent event = new PaymentProcessedEvent(
                savedPayment.getId().value(),
                savedPayment.getOrderId(),
                savedPayment.getAmount().value().toString(),
                "APPROVED",
                savedPayment.getId().value()
        );
        eventPublisher.publish(event);
    }

    public CompletableFuture<Void> processPaymentAsync(String orderId, double amount, String paymentMethod, String authenticatedCustomerId) {
        Payment pendingPayment = processPayment(orderId, amount, paymentMethod, authenticatedCustomerId);
        return CompletableFuture.runAsync(() -> completePayment(pendingPayment.getId().value(), authenticatedCustomerId), taskExecutor);
    }
}
