package com.meridian.event.domain.service;

import com.meridian.event.domain.model.InventoryItem;
import com.meridian.event.domain.model.valueobjects.Sku;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryReserverTest {

    @Test
    void shouldReserveInventorySuccessfully() {
        Map<Sku, InventoryItem> inventory = Map.of(
                Sku.of("SKU-1"), new InventoryItem(Sku.of("SKU-1"), 10)
        );
        InventoryReserver reserver = new InventoryReserver(inventory);

        InventoryReserver.ReservationResult result = reserver.reserve(Sku.of("SKU-1"), 5);

        assertThat(result.success()).isTrue();
    }

    @Test
    void shouldFailWhenSkuNotFound() {
        Map<Sku, InventoryItem> inventory = Map.of(
                Sku.of("SKU-1"), new InventoryItem(Sku.of("SKU-1"), 10)
        );
        InventoryReserver reserver = new InventoryReserver(inventory);

        InventoryReserver.ReservationResult result = reserver.reserve(Sku.of("SKU-2"), 5);

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("SKU not found");
    }
}
