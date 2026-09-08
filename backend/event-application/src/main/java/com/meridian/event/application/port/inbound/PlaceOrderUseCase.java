package com.meridian.event.application.port.inbound;

import com.meridian.event.domain.model.Order;

import java.util.List;

public interface PlaceOrderUseCase {
    Order placeOrder(String customerId, List<OrderLineInput> lines, String authenticatedCustomerId);
}
