package com.meridian.event.infrastructure.web.rest;

import com.meridian.event.application.port.inbound.CompletePaymentUseCase;
import com.meridian.event.application.port.inbound.ProcessPaymentUseCase;
import com.meridian.event.infrastructure.security.audit.SecurityAuditLogger;
import com.meridian.event.infrastructure.web.dto.PaymentRequest;
import com.meridian.event.infrastructure.web.dto.PaymentResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1")
public class PaymentController {

    private final ProcessPaymentUseCase processPaymentUseCase;
    private final CompletePaymentUseCase completePaymentUseCase;
    private final SecurityAuditLogger auditLogger;

    public PaymentController(ProcessPaymentUseCase processPaymentUseCase,
                             CompletePaymentUseCase completePaymentUseCase,
                             SecurityAuditLogger auditLogger) {
        this.processPaymentUseCase = processPaymentUseCase;
        this.completePaymentUseCase = completePaymentUseCase;
        this.auditLogger = auditLogger;
    }

    @PostMapping("/payments")
    @PreAuthorize("hasRole('OPERATOR')")
    public ResponseEntity<PaymentResponse> processPayment(@Valid @RequestBody PaymentRequest request,
                                                           Authentication authentication) {
        String authenticatedCustomerId = getCustomerId(authentication);

        var payment = processPaymentUseCase.processPayment(
                request.orderId(), request.amount(), request.paymentMethod(), authenticatedCustomerId);

        return ResponseEntity.status(HttpStatus.CREATED).body(PaymentResponse.from(payment));
    }

    @PostMapping("/payments/{paymentId}/complete")
    @PreAuthorize("hasRole('OPERATOR')")
    public ResponseEntity<Void> completePayment(@PathVariable String paymentId,
                                                 Authentication authentication) {
        String authenticatedCustomerId = getCustomerId(authentication);

        CompletableFuture.runAsync(
                () -> completePaymentUseCase.completePayment(paymentId, authenticatedCustomerId)
        );

        return ResponseEntity.accepted().build();
    }

    private String getCustomerId(Authentication authentication) {
        if (authentication.getPrincipal() instanceof org.springframework.security.oauth2.jwt.Jwt jwt) {
            String customerId = jwt.getClaimAsString("customer_id");
            if (customerId != null) {
                return customerId;
            }
            return jwt.getSubject();
        }
        throw new IllegalStateException("Unable to extract customer ID from authentication");
    }
}
