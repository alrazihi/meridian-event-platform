package com.meridian.event.infrastructure.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void shouldSanitizeOrderNotFoundMessage() {
        IllegalArgumentException ex = new IllegalArgumentException("Order not found: order-123");
        var response = handler.handleIllegalArgument(ex);

        assertThat(response.get("message")).isEqualTo("Order not found");
    }

    @Test
    void shouldSanitizeAccessDeniedMessage() {
        IllegalArgumentException ex = new IllegalArgumentException("Access denied to order: order-456");
        var response = handler.handleIllegalArgument(ex);

        assertThat(response.get("message")).isEqualTo("Access denied");
    }

    @Test
    void shouldSanitizeCannotPlaceOrderForAnotherCustomer() {
        IllegalArgumentException ex = new IllegalArgumentException("Cannot place order for another customer");
        var response = handler.handleIllegalArgument(ex);

        assertThat(response.get("message")).isEqualTo("Invalid customer");
    }

    @Test
    void shouldSanitizeCannotProcessPaymentForAnotherCustomer() {
        IllegalStateException ex = new IllegalStateException("Cannot process payment for order belonging to another customer");
        var response = handler.handleIllegalState(ex);

        assertThat(response.get("message")).isEqualTo("Access denied");
    }

    @Test
    void shouldSanitizeInventoryNotFound() {
        IllegalStateException ex = new IllegalStateException("Inventory item not found for SKU: SKU-123");
        var response = handler.handleIllegalState(ex);

        assertThat(response.get("message")).isEqualTo("Invalid SKU");
    }

    @Test
    void shouldSanitizeInsufficientInventory() {
        IllegalStateException ex = new IllegalStateException("Insufficient inventory for SKU: SKU-456");
        var response = handler.handleIllegalState(ex);

        assertThat(response.get("message")).isEqualTo("Insufficient inventory");
    }

    @Test
    void shouldSanitizeSkuNotFound() {
        IllegalArgumentException ex = new IllegalArgumentException("SKU not found: SKU-789");
        var response = handler.handleIllegalArgument(ex);

        assertThat(response.get("message")).isEqualTo("Invalid SKU");
    }

    @Test
    void shouldSanitizeAmountMismatch() {
        IllegalStateException ex = new IllegalStateException("amount mismatch");
        var response = handler.handleIllegalState(ex);

        assertThat(response.get("message")).isEqualTo("Invalid payment amount");
    }

    @Test
    void shouldSanitizeMustBePositive() {
        IllegalStateException ex = new IllegalStateException("must be positive");
        var response = handler.handleIllegalState(ex);

        assertThat(response.get("message")).isEqualTo("Invalid amount");
    }

    @Test
    void shouldSanitizeTenantIdRequired() {
        IllegalArgumentException ex = new IllegalArgumentException("Tenant ID is required");
        var response = handler.handleIllegalArgument(ex);

        assertThat(response.get("message")).isEqualTo("Tenant ID required");
    }

    @Test
    void shouldHandleAccessDeniedException() {
        org.springframework.security.access.AccessDeniedException ex = 
                new org.springframework.security.access.AccessDeniedException("Access denied");
        var response = handler.handleAccessDenied(ex);

        assertThat(response.get("status")).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(response.get("message")).isEqualTo("Access denied");
    }

    @Test
    void shouldReturnGenericMessageForUnknownRuntimeException() {
        RuntimeException ex = new RuntimeException("Internal database error: connection timeout");
        var response = handler.handleRuntime(ex);

        assertThat(response.get("status")).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(response.get("message")).isEqualTo("An unexpected error occurred");
    }

    @Test
    void shouldIncludeTimestampInResponse() {
        IllegalArgumentException ex = new IllegalArgumentException("Test error");
        var response = handler.handleIllegalArgument(ex);

        assertThat(response.get("timestamp")).isNotNull();
        assertThat(response.get("timestamp")).isInstanceOf(String.class);
    }
}