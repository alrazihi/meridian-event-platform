package com.meridian.event.infrastructure.persistence.mapper;

import com.meridian.event.domain.model.InventoryItem;
import com.meridian.event.domain.model.valueobjects.Sku;
import com.meridian.event.infrastructure.persistence.jpa.InventoryItemEntity;

import java.time.Instant;

public class InventoryItemMapper {

    public static InventoryItemEntity toEntity(InventoryItem item) {
        InventoryItemEntity entity = new InventoryItemEntity();
        entity.setSku(item.getSku().value());
        entity.setAvailableQuantity(item.getAvailableQuantity());
        entity.setReservedQuantity(item.getReservedQuantity());
        entity.setVersion(item.getVersion());
        entity.setCreatedAt(item.getCreatedAt());
        entity.setUpdatedAt(item.getUpdatedAt());
        return entity;
    }

    public static InventoryItem toDomain(InventoryItemEntity entity) {
        InventoryItem item = new InventoryItem(
                Sku.of(entity.getSku()),
                entity.getAvailableQuantity()
        );

        item.setAvailableQuantity(entity.getAvailableQuantity());
        item.setReservedQuantity(entity.getReservedQuantity());
        item.setVersion(entity.getVersion());
        item.setCreatedAt(entity.getCreatedAt());
        item.setUpdatedAt(entity.getUpdatedAt());
        return item;
    }
}