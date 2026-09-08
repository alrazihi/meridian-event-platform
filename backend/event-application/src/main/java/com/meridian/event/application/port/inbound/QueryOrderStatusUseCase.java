package com.meridian.event.application.port.inbound;

import com.meridian.event.domain.model.Order;

public interface QueryOrderStatusUseCase {
    Order getOrderStatus(String orderId, String authenticatedCustomerId);
}
