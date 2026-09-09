package com.meridian.event.application.service;

import com.meridian.event.application.port.inbound.ReserveInventoryUseCase;
import com.meridian.event.application.port.outbound.InventoryItemRepository;
import com.meridian.event.domain.model.InventoryItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@Import(TestConfig.class)
@Transactional
class DefaultInventoryServiceIntegrationTest {

    @Autowired
    private ReserveInventoryUseCase reserveInventoryUseCase;

    @Autowired
    private InventoryItemRepository inventoryRepository;

    @BeforeEach
    void setUp() {
        // Create test inventory item
        InventoryItem item = new InventoryItem(com.meridian.event.domain.model.valueobjects.Sku.of("SKU-TEST"), 100);
        inventoryRepository.save(item);
    }

    @Test
    void shouldReserveInventorySuccessfully() {
        InventoryItem item = reserveInventoryUseCase.reserveInventory("SKU-TEST", 30, "tenant-123");

        assertThat(item.getSku().value()).isEqualTo("SKU-TEST");
        assertThat(item.getAvailableQuantity()).isEqualTo(70);
        assertThat(item.getReservedQuantity()).isEqualTo(30);
    }

    @Test
    void shouldFailWhenSkuNotFound() {
        assertThatThrownBy(() -> reserveInventoryUseCase.reserveInventory("NON-EXISTENT", 10, "tenant-123"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Inventory item not found");
    }

    @Test
    void shouldFailWhenInsufficientInventory() {
        assertThatThrownBy(() -> reserveInventoryUseCase.reserveInventory("SKU-TEST", 150, "tenant-123"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Insufficient inventory");
    }

    @Test
    void shouldFailWhenQuantityZeroOrNegative() {
        assertThatThrownBy(() -> reserveInventoryUseCase.reserveInventory("SKU-TEST", 0, "tenant-123"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Reserve quantity must be positive");

        assertThatThrownBy(() -> reserveInventoryUseCase.reserveInventory("SKU-TEST", -5, "tenant-123"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Reserve quantity must be positive");
    }
}