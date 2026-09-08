package com.meridian.event.infrastructure.web.rest;

import com.meridian.event.application.port.inbound.PlaceOrderUseCase;
import com.meridian.event.application.port.inbound.QueryOrderStatusUseCase;
import com.meridian.event.application.port.inbound.OrderLineInput;
import com.meridian.event.infrastructure.web.dto.PlaceOrderRequest;
import com.meridian.event.infrastructure.web.dto.OrderResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class OrderController {

    private final PlaceOrderUseCase placeOrderUseCase;
    private final QueryOrderStatusUseCase queryOrderStatusUseCase;

    public OrderController(PlaceOrderUseCase placeOrderUseCase, QueryOrderStatusUseCase queryOrderStatusUseCase) {
        this.placeOrderUseCase = placeOrderUseCase;
        this.queryOrderStatusUseCase = queryOrderStatusUseCase;
    }

    @PostMapping("/orders")
    @PreAuthorize("hasRole('OPERATOR')")
    public ResponseEntity<OrderResponse> placeOrder(@RequestBody PlaceOrderRequest request) {
        var order = placeOrderUseCase.placeOrder(request.customerId(), request.lines());
        OrderResponse response = OrderResponse.from(order);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/orders/{orderId}")
    @PreAuthorize("hasRole('OPERATOR') or hasRole('REVIEWER') or hasRole('ADMIN')")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable String orderId) {
        var order = queryOrderStatusUseCase.getOrderStatus(orderId);
        OrderResponse response = OrderResponse.from(order);
        return ResponseEntity.ok(response);
    }
}
