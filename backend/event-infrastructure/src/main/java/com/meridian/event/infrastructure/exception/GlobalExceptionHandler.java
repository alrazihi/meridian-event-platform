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

    @ExceptionHandler(IllegalArgumentException.class)
    public Map<String, Object> handleIllegalArgument(IllegalArgumentException ex) {
        // Sanitize: don't expose internal details like "Order not found: xyz"
        String message = sanitizeMessage(ex.getMessage());
        return errorResponse(HttpStatus.BAD_REQUEST, "Bad Request", message);
    }

    @ExceptionHandler(IllegalStateException.class)
    public Map<String, Object> handleIllegalState(IllegalStateException ex) {
        String message = sanitizeMessage(ex.getMessage());
        return errorResponse(HttpStatus.CONFLICT, "Conflict", message);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public Map<String, Object> handleAccessDenied(AccessDeniedException ex) {
        return errorResponse(HttpStatus.FORBIDDEN, "Forbidden", "Access denied");
    }

    @ExceptionHandler(RuntimeException.class)
    public Map<String, Object> handleRuntime(RuntimeException ex) {
        // Log the full exception internally (not shown here for brevity)
        return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", "An unexpected error occurred");
    }

    private String sanitizeMessage(String raw) {
        if (raw == null) return "Invalid request";
        // Remove internal identifiers, stack traces, SQL details
        return raw
                .replaceAll("Order not found: .*", "Order not found")
                .replaceAll("Order already has an approved payment", "Payment already processed")
                .replaceAll("Cannot place order for another customer", "Invalid customer")
                .replaceAll("Access denied to order: .*", "Access denied")
                .replaceAll("Cannot process payment for order belonging to another customer", "Access denied")
                .replaceAll("Inventory item not found for SKU: .*", "Invalid SKU")
                .replaceAll("Insufficient inventory for SKU: .*", "Insufficient inventory")
                .replaceAll("SKU not found: .*", "Invalid SKU")
                .replaceAll("amount mismatch", "Invalid payment amount")
                .replaceAll("must be positive", "Invalid amount")
                .replaceAll("Tenant ID is required", "Tenant ID required");
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
