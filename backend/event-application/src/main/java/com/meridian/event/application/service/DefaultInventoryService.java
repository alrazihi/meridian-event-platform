package com.meridian.event.application.service;

import com.meridian.event.application.port.inbound.ReserveInventoryUseCase;
import com.meridian.event.application.port.outbound.AuthorizationService;
import com.meridian.event.application.port.outbound.ClientIpResolver;
import com.meridian.event.application.port.outbound.EventPublisher;
import com.meridian.event.application.port.outbound.InventoryItemRepository;
import com.meridian.event.domain.model.InventoryItem;
import com.meridian.event.domain.model.InventoryReservedEvent;
import com.meridian.event.domain.model.valueobjects.Sku;

import java.util.Optional;

public class DefaultInventoryService implements ReserveInventoryUseCase {

    private final InventoryItemRepository inventoryRepository;
    private final EventPublisher eventPublisher;
    private final AuthorizationService authorizationService;
    private final ClientIpResolver clientIpResolver;

    public DefaultInventoryService(
            InventoryItemRepository inventoryRepository,
            EventPublisher eventPublisher,
            AuthorizationService authorizationService,
            ClientIpResolver clientIpResolver) {
        this.inventoryRepository = inventoryRepository;
        this.eventPublisher = eventPublisher;
        this.authorizationService = authorizationService;
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    public InventoryItem reserveInventory(String sku, int quantity, String tenantId) {
        Sku skuObj = Sku.of(sku);

        Optional<InventoryItem> existingItem = inventoryRepository.findById(skuObj);
        if (existingItem.isEmpty()) {
            throw new IllegalStateException("Inventory item not found for SKU: " + sku);
        }

        InventoryItem item = existingItem.get();

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
