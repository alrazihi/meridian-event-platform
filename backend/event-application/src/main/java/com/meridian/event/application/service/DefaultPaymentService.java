package com.meridian.event.application.service;

import com.meridian.event.application.port.inbound.ProcessPaymentUseCase;
import com.meridian.event.application.port.outbound.EventPublisher;
import com.meridian.event.application.port.outbound.NotificationService;
import com.meridian.event.application.port.outbound.OrderRepository;
import com.meridian.event.application.port.outbound.PaymentRepository;
import com.meridian.event.application.port.outbound.PaymentGateway;
import com.meridian.event.domain.model.Order;
import com.meridian.event.domain.model.Payment;
import com.meridian.event.domain.model.PaymentStatus;
import com.meridian.event.domain.model.valueobjects.Money;
import com.meridian.event.domain.model.valueobjects.OrderId;
import com.meridian.event.domain.model.valueobjects.PaymentId;
import com.meridian.event.domain.model.PaymentProcessedEvent;
import com.meridian.event.infrastructure.observability.BusinessMetrics;
import com.meridian.event.infrastructure.observability.CorrelationIdContext;
import com.meridian.event.infrastructure.security.audit.SecurityAuditLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletRequest;
import java.util.concurrent.CompletableFuture;

@Service
public class DefaultPaymentService implements ProcessPaymentUseCase {

    private static final Logger log = LoggerFactory.getLogger("WORKFLOW");

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final EventPublisher eventPublisher;
    private final NotificationService notificationService;
    private final PaymentGateway paymentGateway;
    private final SecurityAuditLogger auditLogger;
    private final HttpServletRequest request;
    private final BusinessMetrics businessMetrics;

    public DefaultPaymentService(
            OrderRepository orderRepository,
            PaymentRepository paymentRepository,
            EventPublisher eventPublisher,
            NotificationService notificationService,
            PaymentGateway paymentGateway,
            SecurityAuditLogger auditLogger,
            HttpServletRequest request,
            BusinessMetrics businessMetrics) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.eventPublisher = eventPublisher;
        this.notificationService = notificationService;
        this.paymentGateway = paymentGateway;
        this.auditLogger = auditLogger;
        this.request = request;
        this.businessMetrics = businessMetrics;
    }

    @Override
    @Transactional
    public Payment processPayment(String orderId, double amount, String paymentMethod, String authenticatedCustomerId) {
        String correlationId = CorrelationIdContext.getCorrelationId();
        String ip = getClientIp();
        OrderId oid = OrderId.from(orderId);
        Order order = orderRepository.findById(oid)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        log.info("Payment initiation started paymentId={} orderId={} customerId={} amount={} correlationId={}",
                "pending", orderId, authenticatedCustomerId, amount, correlationId);

        if (!isAdmin() && !order.getCustomerId().equals(authenticatedCustomerId)) {
            auditLogger.logAuthorizationDenied(authenticatedCustomerId, ip, "/api/payments", "order:" + orderId, "Customer does not own this order");
            log.warn("Payment authorization denied orderId={} customerId={} correlationId={}", orderId, authenticatedCustomerId, correlationId);
            throw new AccessDeniedException("Cannot process payment for order belonging to another customer");
        }

        if (paymentRepository.existsByOrderIdAndStatus(orderId, PaymentStatus.APPROVED)) {
            auditLogger.logPaymentAttempt(authenticatedCustomerId, ip, orderId, String.valueOf(amount), "DUPLICATE");
            log.warn("Duplicate payment attempt orderId={} customerId={} correlationId={}", orderId, authenticatedCustomerId, correlationId);
            throw new IllegalStateException("Order already has an approved payment");
        }

        PaymentId paymentId = PaymentId.generate();
        Payment payment = new Payment(
                paymentId, orderId, Money.of(new java.math.BigDecimal(amount), "USD"), paymentMethod
        );

        Payment pendingPayment = paymentRepository.save(payment);

        auditLogger.logPaymentAttempt(authenticatedCustomerId, ip, orderId, String.valueOf(amount), "PENDING");
        log.info("Payment pending paymentId={} orderId={} customerId={} amount={} correlationId={}",
                paymentId.value(), orderId, authenticatedCustomerId, amount, correlationId);

        businessMetrics.incrementPaymentsInitiated();

        return pendingPayment;
    }

    @Transactional
    public void completePayment(String paymentId, String authenticatedCustomerId) {
        String correlationId = CorrelationIdContext.getCorrelationId();
        String ip = getClientIp();
        Payment payment = paymentRepository.findById(com.meridian.event.domain.model.valueobjects.PaymentId.from(paymentId))
                .orElseThrow(() -> new IllegalArgumentException("Payment not found"));

        log.info("Payment completion started paymentId={} orderId={} correlationId={}", paymentId, payment.getOrderId(), correlationId);

        if (payment.getStatus() != PaymentStatus.PENDING) {
            auditLogger.logPaymentAttempt(authenticatedCustomerId, ip, payment.getOrderId(), payment.getAmount().value().toString(), "INVALID_STATE");
            log.warn("Payment invalid state paymentId={} status={} correlationId={}", paymentId, payment.getStatus(), correlationId);
            throw new IllegalStateException("Payment is not in PENDING state");
        }

        PaymentGateway.ProcessingResult result = paymentGateway.charge(payment, payment.getAmount());

        if (!result.success()) {
            payment.reject(result.errorMessage());
            paymentRepository.save(payment);
            auditLogger.logPaymentAttempt(authenticatedCustomerId, ip, payment.getOrderId(), payment.getAmount().value().toString(), "FAILED");
            log.warn("Payment gateway failed paymentId={} error={} correlationId={}", paymentId, result.errorMessage(), correlationId);
            businessMetrics.incrementPaymentsRejected();
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

        auditLogger.logPaymentAttempt(authenticatedCustomerId, ip, payment.getOrderId(), payment.getAmount().value().toString(), "SUCCESS");
        log.info("Payment approved paymentId={} transactionId={} orderId={} correlationId={}",
                paymentId, result.transactionId(), payment.getOrderId(), correlationId);

        businessMetrics.incrementPaymentsApproved();
        businessMetrics.incrementEventsPublished();
    }

    public CompletableFuture<Void> processPaymentAsync(String orderId, double amount, String paymentMethod, String authenticatedCustomerId) {
        Payment pendingPayment = processPayment(orderId, amount, paymentMethod, authenticatedCustomerId);
        return CompletableFuture.runAsync(() -> completePayment(pendingPayment.getId().value(), authenticatedCustomerId));
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


