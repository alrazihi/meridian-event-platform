package com.meridian.event.application.port.outbound;

import com.meridian.event.domain.model.InventoryItem;
import com.meridian.event.domain.model.valueobjects.Sku;

import java.util.Optional;

public interface InventoryItemRepository {
    InventoryItem save(InventoryItem item);
    Optional<InventoryItem> findById(Sku sku);
}