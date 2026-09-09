package com.meridian.event.infrastructure.web.dto;

import com.meridian.event.application.port.inbound.OrderLineInput;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PlaceOrderRequest(
        @NotNull(message = "Order lines are required")
        @Size(min = 1, message = "At least one order line is required")
        List<OrderLineInput> lines
) {
}
