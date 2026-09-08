package com.meridian.event.infrastructure.web.dto;

import java.util.List;

public record PlaceOrderRequest(
        String customerId,
        List<OrderLineInput> lines
) {
}
