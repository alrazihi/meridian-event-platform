package com.meridian.event.infrastructure.web.rest;

import com.meridian.event.application.port.inbound.PlaceOrderUseCase;
import com.meridian.event.application.port.inbound.QueryOrderStatusUseCase;
import com.meridian.event.infrastructure.security.audit.SecurityAuditLogger;
import com.meridian.event.infrastructure.web.dto.PlaceOrderRequest;
import com.meridian.event.infrastructure.web.dto.OrderResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class OrderController {

    private final PlaceOrderUseCase placeOrderUseCase;
    private final QueryOrderStatusUseCase queryOrderStatusUseCase;
    private final SecurityAuditLogger auditLogger;

    public OrderController(PlaceOrderUseCase placeOrderUseCase, 
                           QueryOrderStatusUseCase queryOrderStatusUseCase,
                           SecurityAuditLogger auditLogger) {
        this.placeOrderUseCase = placeOrderUseCase;
        this.queryOrderStatusUseCase = queryOrderStatusUseCase;
        this.auditLogger = auditLogger;
    }

    @PostMapping("/orders")
    @PreAuthorize("hasRole('OPERATOR')")
    public ResponseEntity<OrderResponse> placeOrder(@Valid @RequestBody PlaceOrderRequest request, 
                                                     Authentication authentication,
                                                     HttpServletRequest httpRequest) {
        String authenticatedCustomerId = getCustomerIdFromToken(authentication);
        String ip = httpRequest.getRemoteAddr();
        
        auditLogger.logOrderAccess(authenticatedCustomerId, ip, "new", "PLACE_ORDER");
        
        var order = placeOrderUseCase.placeOrder(request.customerId(), request.lines(), authenticatedCustomerId);
        OrderResponse response = OrderResponse.from(order);
        
        auditLogger.logOrderAccess(authenticatedCustomerId, ip, order.getId().value(), "ORDER_PLACED");
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/orders/{orderId}")
    @PreAuthorize("hasRole('OPERATOR') or hasRole('REVIEWER') or hasRole('ADMIN')")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable String orderId, 
                                                   Authentication authentication,
                                                   HttpServletRequest httpRequest) {
        String authenticatedCustomerId = getCustomerIdFromToken(authentication);
        String ip = httpRequest.getRemoteAddr();
        
        auditLogger.logOrderAccess(authenticatedCustomerId, ip, orderId, "VIEW_ATTEMPT");
        
        var order = queryOrderStatusUseCase.getOrderStatus(orderId, authenticatedCustomerId);
        OrderResponse response = OrderResponse.from(order);
        
        auditLogger.logOrderAccess(authenticatedCustomerId, ip, orderId, "VIEW_SUCCESS");
        
        return ResponseEntity.ok(response);
    }

    private String getCustomerIdFromToken(Authentication authentication) {
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            String customerId = jwt.getClaimAsString("customer_id");
            if (customerId != null) {
                return customerId;
            }
            return jwt.getSubject();
        }
        throw new IllegalStateException("Unable to extract customer ID from authentication");
    }
}
