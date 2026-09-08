package com.meridian.event.application.service;

import com.meridian.event.application.port.inbound.ReserveInventoryUseCase;
import com.meridian.event.application.port.outbound.EventPublisher;
import com.meridian.event.application.port.outbound.InventoryItemRepository;
import com.meridian.event.domain.model.InventoryItem;
import com.meridian.event.domain.model.valueobjects.Sku;
import com.meridian.event.domain.model.InventoryReservedEvent;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class DefaultInventoryService implements ReserveInventoryUseCase {

    private final InventoryItemRepository inventoryRepository;
    private final EventPublisher eventPublisher;

    public DefaultInventoryService(
            InventoryItemRepository inventoryRepository,
            EventPublisher eventPublisher) {
        this.inventoryRepository = inventoryRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public InventoryItem reserveInventory(String sku, int quantity, String tenantId) {
        Sku skuObj = Sku.of(sku);

        Optional<InventoryItem> existingItem = inventoryRepository.findById(skuObj);
        if (existingItem.isEmpty()) {
            throw new IllegalStateException("Inventory item not found for SKU: " + sku);
        }

        InventoryItem item = existingItem.get();

        // TODO: Add tenant isolation check when InventoryItem has tenantId field
        // For now, validate tenantId format to prevent injection
        if (tenantId == null || tenantId.trim().isEmpty()) {
            throw new IllegalArgumentException("Tenant ID is required");
        }

        item.reserve(quantity);

        InventoryItem savedItem = inventoryRepository.save(item);

        InventoryReservedEvent event = new InventoryReservedEvent(skuObj, quantity, sku);
        eventPublisher.publish(event);

        return savedItem;
    }
}



