package com.meridian.event.infrastructure.web.dto;

import com.meridian.event.domain.model.Order;

import java.math.BigDecimal;

public record OrderResponse(
        String orderId,
        String customerId,
        BigDecimal total,
        String status,
        java.time.Instant createdAt
) {
    public static OrderResponse from(Order order) {
        if (order == null) {
            return null;
        }
        return new OrderResponse(
                order.getId().value(),
                order.getCustomerId(),
                order.getTotal().value(),
                order.getStatus().name(),
                order.getCreatedAt()
        );
    }
}
