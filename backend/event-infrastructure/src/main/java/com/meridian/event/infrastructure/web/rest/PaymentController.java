package com.meridian.event.infrastructure.web.rest;

import com.meridian.event.application.port.inbound.CompletePaymentUseCase;
import com.meridian.event.application.port.inbound.ProcessPaymentUseCase;
import com.meridian.event.infrastructure.security.audit.SecurityAuditLogger;
import com.meridian.event.infrastructure.web.dto.PaymentRequest;
import com.meridian.event.infrastructure.web.dto.PaymentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.Executor;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Payments", description = "Payment management API")
public class PaymentController {

    private final ProcessPaymentUseCase processPaymentUseCase;
    private final CompletePaymentUseCase completePaymentUseCase;
    private final SecurityAuditLogger auditLogger;
    private final Executor paymentTaskExecutor;

    public PaymentController(ProcessPaymentUseCase processPaymentUseCase,
                             CompletePaymentUseCase completePaymentUseCase,
                             SecurityAuditLogger auditLogger,
                             Executor paymentTaskExecutor) {
        this.processPaymentUseCase = processPaymentUseCase;
        this.completePaymentUseCase = completePaymentUseCase;
        this.auditLogger = auditLogger;
        this.paymentTaskExecutor = paymentTaskExecutor;
    }

    @PostMapping("/payments")
    @PreAuthorize("hasRole('OPERATOR')")
    @Operation(summary = "Process a payment", description = "Initiates a payment for an order")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Payment initiated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
            @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content)
    })
    public ResponseEntity<PaymentResponse> processPayment(@Valid @RequestBody PaymentRequest request,
                                                           Authentication authentication) {
        String authenticatedCustomerId = getCustomerId(authentication);

        var payment = processPaymentUseCase.processPayment(
                request.orderId(), request.amount(), request.paymentMethod(), authenticatedCustomerId);

        return ResponseEntity.status(HttpStatus.CREATED).body(PaymentResponse.from(payment));
    }

    @PostMapping("/payments/{paymentId}/complete")
    @PreAuthorize("hasRole('OPERATOR')")
    @Operation(summary = "Complete a payment", description = "Asynchronously completes a pending payment")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Payment completion accepted"),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
            @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content),
            @ApiResponse(responseCode = "404", description = "Payment not found", content = @Content)
    })
    public ResponseEntity<Void> completePayment(@PathVariable String paymentId,
                                                 Authentication authentication) {
        String authenticatedCustomerId = getCustomerId(authentication);

        java.util.concurrent.CompletableFuture.runAsync(
                () -> completePaymentUseCase.completePayment(paymentId, authenticatedCustomerId),
                paymentTaskExecutor
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
