package com.meridian.event.infrastructure.exception;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // Allowlist of safe error messages - only these exact messages are returned to clients
    private static final Map<String, String> SAFE_MESSAGES = Map.ofEntries(
            Map.entry("ORDER_NOT_FOUND", "Order not found"),
            Map.entry("ORDER_ALREADY_PAID", "Payment already processed"),
            Map.entry("INVALID_CUSTOMER", "Invalid customer"),
            Map.entry("ACCESS_DENIED", "Access denied"),
            Map.entry("INVALID_SKU", "Invalid SKU"),
            Map.entry("INSUFFICIENT_INVENTORY", "Insufficient inventory"),
            Map.entry("INVALID_PAYMENT_AMOUNT", "Invalid payment amount"),
            Map.entry("INVALID_AMOUNT", "Invalid amount"),
            Map.entry("TENANT_ID_REQUIRED", "Tenant ID required"),
            Map.entry("VALIDATION_ERROR", "Invalid request"),
            Map.entry("PAYMENT_PROCESSING_FAILED", "Payment processing failed"),
            Map.entry("INVENTORY_RESERVATION_FAILED", "Inventory reservation failed")
    );

    @ExceptionHandler(IllegalArgumentException.class)
    public Map<String, Object> handleIllegalArgument(IllegalArgumentException ex) {
        String safeMessage = mapToSafeMessage(ex.getMessage(), "VALIDATION_ERROR");
        return errorResponse(HttpStatus.BAD_REQUEST, "Bad Request", safeMessage);
    }

    @ExceptionHandler(IllegalStateException.class)
    public Map<String, Object> handleIllegalState(IllegalStateException ex) {
        String safeMessage = mapToSafeMessage(ex.getMessage(), "VALIDATION_ERROR");
        return errorResponse(HttpStatus.CONFLICT, "Conflict", safeMessage);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public Map<String, Object> handleAccessDenied(AccessDeniedException ex) {
        return errorResponse(HttpStatus.FORBIDDEN, "Forbidden", SAFE_MESSAGES.get("ACCESS_DENIED"));
    }

    @ExceptionHandler(RuntimeException.class)
    public Map<String, Object> handleRuntime(RuntimeException ex) {
        // Log the full exception internally (not shown here for brevity)
        return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", "An unexpected error occurred");
    }

    private String mapToSafeMessage(String raw, String defaultKey) {
        if (raw == null) return SAFE_MESSAGES.get(defaultKey);

        // Map known error patterns to safe messages using exact matching where possible
        if (raw.contains("Order not found")) return SAFE_MESSAGES.get("ORDER_NOT_FOUND");
        if (raw.contains("already has an approved payment")) return SAFE_MESSAGES.get("ORDER_ALREADY_PAID");
        if (raw.contains("Cannot place order for another customer")) return SAFE_MESSAGES.get("INVALID_CUSTOMER");
        if (raw.contains("Access denied")) return SAFE_MESSAGES.get("ACCESS_DENIED");
        if (raw.contains("Cannot process payment for order belonging to another customer")) return SAFE_MESSAGES.get("ACCESS_DENIED");
        if (raw.contains("Inventory item not found") || raw.contains("SKU not found")) return SAFE_MESSAGES.get("INVALID_SKU");
        if (raw.contains("Insufficient inventory")) return SAFE_MESSAGES.get("INSUFFICIENT_INVENTORY");
        if (raw.contains("amount mismatch")) return SAFE_MESSAGES.get("INVALID_PAYMENT_AMOUNT");
        if (raw.contains("must be positive")) return SAFE_MESSAGES.get("INVALID_AMOUNT");
        if (raw.contains("Tenant ID is required")) return SAFE_MESSAGES.get("TENANT_ID_REQUIRED");

        return SAFE_MESSAGES.get(defaultKey);
    }

    private Map<String, Object> errorResponse(HttpStatus status, String title, String detail) {
        Map<String, Object> error = new HashMap<>();
        error.put("status", status.value());
        error.put("error", title);
        error.put("message", detail);
        error.put("timestamp", Instant.now().toString());
        return error;
    }
}
