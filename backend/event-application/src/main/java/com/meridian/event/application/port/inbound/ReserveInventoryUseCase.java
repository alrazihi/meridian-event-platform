package com.meridian.event.application.port.inbound;

import com.meridian.event.domain.model.InventoryItem;

public interface ReserveInventoryUseCase {
    InventoryItem reserveInventory(String sku, int quantity, String tenantId);
}
