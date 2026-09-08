package com.meridian.event.infrastructure.web.dto;

import com.meridian.event.application.port.inbound.OrderLineInput;
import java.util.List;

public record PlaceOrderRequest(
        String customerId,
        List<OrderLineInput> lines
) {
}
