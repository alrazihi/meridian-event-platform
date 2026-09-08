package com.meridian.event.application.service;

import com.meridian.event.application.port.inbound.ReserveInventoryUseCase;
import com.meridian.event.application.port.outbound.EventPublisher;
import com.meridian.event.application.port.outbound.OrderRepository;
import com.meridian.event.domain.model.DomainEvent;
import com.meridian.event.domain.model.InventoryItem;
import com.meridian.event.domain.model.valueobjects.Sku;
import com.meridian.event.domain.service.InventoryReserver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DefaultInventoryService implements ReserveInventoryUseCase {

    private final OrderRepository orderRepository;
    private final EventPublisher eventPublisher;
    private final InventoryReserver inventoryReserver;
    private final Map<Sku, InventoryItem> inventory = new ConcurrentHashMap<>();

    public DefaultInventoryService(
            OrderRepository orderRepository,
            EventPublisher eventPublisher,
            InventoryReserver inventoryReserver) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
        this.inventoryReserver = inventoryReserver;
    }

    @Override
    @Transactional
    public InventoryItem reserveInventory(String sku, int quantity) {
        Sku skuObj = Sku.of(sku);
        InventoryReserver.ReservationResult result = inventoryReserver.reserve(skuObj, quantity);
        if (!result.success()) {
            throw new IllegalStateException(result.errorMessage());
        }

        InventoryReservedEvent event = new InventoryReservedEvent(
                sku,
                quantity,
                sku
        );
        eventPublisher.publish(event);

        return inventory.get(skuObj);
    }
}
