package com.meridian.event.domain.model;

import com.meridian.event.domain.exception.DomainException;
import com.meridian.event.domain.model.valueobjects.Sku;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryItemTest {

    @Test
    void shouldCreateInventoryItem() {
        Sku sku = Sku.of("SKU-123");

        InventoryItem item = new InventoryItem(sku, 100);

        assertThat(item.getSku()).isEqualTo(sku);
        assertThat(item.getAvailableQuantity()).isEqualTo(100);
        assertThat(item.getReservedQuantity()).isEqualTo(0);
        assertThat(item.getVersion()).isEqualTo(0L);
        assertThat(item.getCreatedAt()).isNotNull();
        assertThat(item.getUpdatedAt()).isNotNull();
    }

    @Test
    void shouldReserveQuantity() {
        InventoryItem item = new InventoryItem(Sku.of("SKU-1"), 100);

        item.reserve(30);

        assertThat(item.getAvailableQuantity()).isEqualTo(70);
        assertThat(item.getReservedQuantity()).isEqualTo(30);
        assertThat(item.getVersion()).isEqualTo(1L);
        assertThat(item.getUpdatedAt()).isAfter(item.getCreatedAt());
    }

    @Test
    void shouldFailToReserveMoreThanAvailable() {
        InventoryItem item = new InventoryItem(Sku.of("SKU-1"), 10);

        assertThatThrownBy(() -> item.reserve(15))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Insufficient inventory for SKU: SKU-1");
    }

    @Test
    void shouldFailToReserveZeroOrNegative() {
        InventoryItem item = new InventoryItem(Sku.of("SKU-1"), 100);

        assertThatThrownBy(() -> item.reserve(0))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Reserve quantity must be positive");

        assertThatThrownBy(() -> item.reserve(-5))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Reserve quantity must be positive");
    }

    @Test
    void shouldReleaseQuantity() {
        InventoryItem item = new InventoryItem(Sku.of("SKU-1"), 100);
        item.reserve(30);

        item.release(10);

        assertThat(item.getAvailableQuantity()).isEqualTo(90);
        assertThat(item.getReservedQuantity()).isEqualTo(20);
        assertThat(item.getVersion()).isEqualTo(2L);
    }

    @Test
    void shouldFailToReleaseMoreThanReserved() {
        InventoryItem item = new InventoryItem(Sku.of("SKU-1"), 100);
        item.reserve(30);

        assertThatThrownBy(() -> item.release(35))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Cannot release more than reserved for SKU: SKU-1");
    }

    @Test
    void shouldFailToReleaseZeroOrNegative() {
        InventoryItem item = new InventoryItem(Sku.of("SKU-1"), 100);
        item.reserve(30);

        assertThatThrownBy(() -> item.release(0))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Release quantity must be positive");

        assertThatThrownBy(() -> item.release(-5))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Release quantity must be positive");
    }

    @Test
    void shouldHandleFullReservationAndReleaseCycle() {
        InventoryItem item = new InventoryItem(Sku.of("SKU-1"), 100);

        item.reserve(100);
        assertThat(item.getAvailableQuantity()).isEqualTo(0);
        assertThat(item.getReservedQuantity()).isEqualTo(100);

        item.release(100);
        assertThat(item.getAvailableQuantity()).isEqualTo(100);
        assertThat(item.getReservedQuantity()).isEqualTo(0);
    }
}