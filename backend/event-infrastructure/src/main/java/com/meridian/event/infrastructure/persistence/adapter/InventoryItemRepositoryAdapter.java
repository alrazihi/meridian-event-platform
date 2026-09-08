package com.meridian.event.infrastructure.persistence.adapter;

import com.meridian.event.application.port.outbound.InventoryItemRepository;
import com.meridian.event.domain.model.InventoryItem;
import com.meridian.event.domain.model.valueobjects.Sku;
import com.meridian.event.infrastructure.persistence.jpa.InventoryItemEntity;
import com.meridian.event.infrastructure.persistence.mapper.InventoryItemMapper;
import com.meridian.event.infrastructure.persistence.repository.InventoryItemJpaRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class InventoryItemRepositoryAdapter implements InventoryItemRepository {

    private final InventoryItemJpaRepository jpaRepository;

    public InventoryItemRepositoryAdapter(InventoryItemJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public InventoryItem save(InventoryItem item) {
        InventoryItemEntity entity = InventoryItemMapper.toEntity(item);
        InventoryItemEntity saved = jpaRepository.save(entity);
        return InventoryItemMapper.toDomain(saved);
    }

    @Override
    public Optional<InventoryItem> findById(Sku sku) {
        return jpaRepository.findById(sku.value())
                .map(InventoryItemMapper::toDomain);
    }
}