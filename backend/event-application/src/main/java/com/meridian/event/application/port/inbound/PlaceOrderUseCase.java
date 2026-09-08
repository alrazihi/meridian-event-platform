package com.meridian.event.application.port.inbound;

import com.meridian.event.domain.model.Order;
import com.meridian.event.domain.model.valueobjects.OrderId;

public interface PlaceOrderUseCase {
    Order placeOrder(String customerId, java.util.List<OrderLineInput> lines);
}
