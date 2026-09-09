package com.meridian.event.infrastructure.persistence.adapter;

import com.meridian.event.application.port.outbound.InventoryItemRepository;
import com.meridian.event.domain.model.InventoryItem;
import com.meridian.event.domain.model.valueobjects.Sku;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@Import(RepositoryTestConfig.class)
@Transactional
class InventoryItemRepositoryAdapterIntegrationTest {

    @Autowired
    private InventoryItemRepository inventoryRepository;

    @Test
    void shouldSaveAndFindInventoryItem() {
        InventoryItem item = new InventoryItem(Sku.of("SKU-123"), 100);

        InventoryItem saved = inventoryRepository.save(item);

        assertThat(saved.getSku().value()).isEqualTo("SKU-123");
        assertThat(saved.getAvailableQuantity()).isEqualTo(100);
        assertThat(saved.getReservedQuantity()).isEqualTo(0);

        Optional<InventoryItem> found = inventoryRepository.findById(Sku.of("SKU-123"));
        assertThat(found).isPresent();
        assertThat(found.get().getAvailableQuantity()).isEqualTo(100);
    }

    @Test
    void shouldUpdateInventoryQuantities() {
        InventoryItem item = new InventoryItem(Sku.of("SKU-456"), 100);
        InventoryItem saved = inventoryRepository.save(item);

        saved.reserve(30);
        InventoryItem updated = inventoryRepository.save(saved);

        assertThat(updated.getAvailableQuantity()).isEqualTo(70);
        assertThat(updated.getReservedQuantity()).isEqualTo(30);
        assertThat(updated.getVersion()).isEqualTo(1L);
    }

    @Test
    void shouldReturnEmptyForNonExistentSku() {
        Optional<InventoryItem> found = inventoryRepository.findById(Sku.of("NON-EXISTENT"));
        assertThat(found).isEmpty();
    }
}
