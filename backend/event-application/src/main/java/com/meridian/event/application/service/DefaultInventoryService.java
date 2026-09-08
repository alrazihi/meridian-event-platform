package com.meridian.event.application.service;

import com.meridian.event.application.port.inbound.ReserveInventoryUseCase;
import com.meridian.event.application.port.outbound.EventPublisher;
import com.meridian.event.application.port.outbound.InventoryItemRepository;
import com.meridian.event.domain.model.InventoryItem;
import com.meridian.event.domain.model.valueobjects.Sku;
import com.meridian.event.domain.model.InventoryReservedEvent;
import com.meridian.event.domain.service.InventoryReserver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class DefaultInventoryService implements ReserveInventoryUseCase {

    private final InventoryItemRepository inventoryRepository;
    private final EventPublisher eventPublisher;
    private final InventoryReserver inventoryReserver;

    public DefaultInventoryService(
            InventoryItemRepository inventoryRepository,
            EventPublisher eventPublisher,
            InventoryReserver inventoryReserver) {
        this.inventoryRepository = inventoryRepository;
        this.eventPublisher = eventPublisher;
        this.inventoryReserver = inventoryReserver;
    }

    @Override
    @Transactional
    public InventoryItem reserveInventory(String sku, int quantity) {
        Sku skuObj = Sku.of(sku);
        
        Optional<InventoryItem> existingItem = inventoryRepository.findById(skuObj);
        if (existingItem.isEmpty()) {
            throw new IllegalStateException("Inventory item not found for SKU: " + sku);
        }
        
        InventoryItem item = existingItem.get();
        item.reserve(quantity);
        
        InventoryItem savedItem = inventoryRepository.save(item);

        InventoryReservedEvent event = new InventoryReservedEvent(skuObj, quantity, sku);
        eventPublisher.publish(event);

        return savedItem;
    }
}



