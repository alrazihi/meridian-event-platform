package com.meridian.event.application.port.outbound;

import com.meridian.event.domain.model.Order;
import com.meridian.event.domain.model.valueobjects.OrderId;

import java.util.Optional;

public interface OrderRepository {
    Order save(Order order);
    Optional<Order> findById(OrderId id);
}
