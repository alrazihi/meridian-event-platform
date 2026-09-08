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
import com.meridian.event.infrastructure.security.audit.SecurityAuditLogger;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletRequest;

@Service
public class DefaultPaymentService implements ProcessPaymentUseCase {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final EventPublisher eventPublisher;
    private final NotificationService notificationService;
    private final PaymentProcessor paymentProcessor;
    private final SecurityAuditLogger auditLogger;
    private final HttpServletRequest request;

    public DefaultPaymentService(
            OrderRepository orderRepository,
            PaymentRepository paymentRepository,
            EventPublisher eventPublisher,
            NotificationService notificationService,
            PaymentProcessor paymentProcessor,
            SecurityAuditLogger auditLogger,
            HttpServletRequest request) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.eventPublisher = eventPublisher;
        this.notificationService = notificationService;
        this.paymentProcessor = paymentProcessor;
        this.auditLogger = auditLogger;
        this.request = request;
    }

    @Override
    @Transactional
    public com.meridian.event.domain.model.Payment processPayment(String orderId, double amount, String paymentMethod, String authenticatedCustomerId) {
        String ip = getClientIp();
        OrderId oid = OrderId.from(orderId);
        Order order = orderRepository.findById(oid)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        // Resource-level authorization: verify the order belongs to the authenticated customer
        // Admins can process payments for any order
        if (!isAdmin() && !order.getCustomerId().equals(authenticatedCustomerId)) {
            auditLogger.logAuthorizationDenied(authenticatedCustomerId, ip, "/api/payments", "order:" + orderId, "Customer does not own this order");
            throw new AccessDeniedException("Cannot process payment for order belonging to another customer");
        }

        // Prevent double payment
        if (paymentRepository.existsByOrderIdAndStatus(orderId, com.meridian.event.domain.model.PaymentStatus.APPROVED)) {
            auditLogger.logPaymentAttempt(authenticatedCustomerId, ip, orderId, String.valueOf(amount), "DUPLICATE");
            throw new IllegalStateException("Order already has an approved payment");
        }

        PaymentId paymentId = PaymentId.generate();
        com.meridian.event.domain.model.Payment payment = new com.meridian.event.domain.model.Payment(
                paymentId, orderId, Money.of(new java.math.BigDecimal(amount), "USD"), paymentMethod
        );

        PaymentProcessor.ProcessingResult result = paymentProcessor.process(payment, payment.getAmount());
        if (!result.success()) {
            auditLogger.logPaymentAttempt(authenticatedCustomerId, ip, orderId, String.valueOf(amount), "FAILED");
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

        auditLogger.logPaymentAttempt(authenticatedCustomerId, ip, orderId, String.valueOf(amount), "SUCCESS");

        return savedPayment;
    }

    private boolean isAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null) {
            return authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        }
        return false;
    }

    private String getClientIp() {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader != null && !xfHeader.isEmpty()) {
            return xfHeader.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}


