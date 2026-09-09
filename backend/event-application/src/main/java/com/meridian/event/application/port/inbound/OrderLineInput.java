package com.meridian.event.application.port.inbound;

public record OrderLineInput(String sku, int quantity, double unitPrice) {
}
