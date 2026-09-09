package com.meridian.event.infrastructure.web.rest;

import com.meridian.event.application.port.inbound.PlaceOrderUseCase;
import com.meridian.event.application.port.inbound.QueryOrderStatusUseCase;
import com.meridian.event.application.port.outbound.AuthorizationService;
import com.meridian.event.infrastructure.security.audit.SecurityAuditLogger;
import com.meridian.event.infrastructure.web.dto.OrderResponse;
import com.meridian.event.infrastructure.web.dto.PlaceOrderRequest;
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

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Orders", description = "Order management API")
public class OrderController {

    private final PlaceOrderUseCase placeOrderUseCase;
    private final QueryOrderStatusUseCase queryOrderStatusUseCase;
    private final SecurityAuditLogger auditLogger;
    private final AuthorizationService authorizationService;

    public OrderController(PlaceOrderUseCase placeOrderUseCase,
                           QueryOrderStatusUseCase queryOrderStatusUseCase,
                           SecurityAuditLogger auditLogger,
                           AuthorizationService authorizationService) {
        this.placeOrderUseCase = placeOrderUseCase;
        this.queryOrderStatusUseCase = queryOrderStatusUseCase;
        this.auditLogger = auditLogger;
        this.authorizationService = authorizationService;
    }

    @PostMapping("/orders")
    @PreAuthorize("hasRole('OPERATOR')")
    @Operation(summary = "Place a new order", description = "Creates a new order for the authenticated customer")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Order created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
            @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content)
    })
    public ResponseEntity<OrderResponse> placeOrder(@Valid @RequestBody PlaceOrderRequest request,
                                                     Authentication authentication) {
        String authenticatedCustomerId = getCustomerId(authentication);

        var order = placeOrderUseCase.placeOrder(authenticatedCustomerId, request.lines(), authenticatedCustomerId);
        OrderResponse response = OrderResponse.from(order);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/orders/{orderId}")
    @PreAuthorize("hasRole('OPERATOR') or hasRole('REVIEWER') or hasRole('ADMIN')")
    @Operation(summary = "Get order status", description = "Retrieves the status of an order. Users can only access their own orders unless they are ADMIN.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Order found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
            @ApiResponse(responseCode = "403", description = "Access denied", content = @Content),
            @ApiResponse(responseCode = "404", description = "Order not found", content = @Content)
    })
    public ResponseEntity<OrderResponse> getOrder(@PathVariable String orderId,
                                                   Authentication authentication) {
        String authenticatedCustomerId = getCustomerId(authentication);

        var order = queryOrderStatusUseCase.getOrderStatus(orderId, authenticatedCustomerId);
        OrderResponse response = OrderResponse.from(order);

        return ResponseEntity.ok(response);
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
