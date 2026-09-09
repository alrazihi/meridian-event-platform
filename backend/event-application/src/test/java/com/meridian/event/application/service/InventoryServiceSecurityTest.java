package com.meridian.event.application.service;

import com.meridian.event.application.port.inbound.ReserveInventoryUseCase;
import com.meridian.event.application.port.outbound.AuthorizationService;
import com.meridian.event.application.port.outbound.ClientIpResolver;
import com.meridian.event.application.port.outbound.EventPublisher;
import com.meridian.event.application.port.outbound.InventoryItemRepository;
import com.meridian.event.domain.model.InventoryItem;
import com.meridian.event.domain.model.valueobjects.Sku;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceSecurityTest {

    @Mock
    private InventoryItemRepository inventoryRepository;

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private ClientIpResolver clientIpResolver;

    private DefaultInventoryService inventoryService;

    @BeforeEach
    void setUp() {
        inventoryService = new DefaultInventoryService(
                inventoryRepository,
                eventPublisher,
                authorizationService,
                clientIpResolver
        );
    }

    @Test
    void shouldAllowValidTenantToReserveInventory() {
        String tenantId = "tenant-123";
        String sku = "SKU-123";
        int quantity = 10;

        InventoryItem item = new InventoryItem(Sku.of(sku), 100);
        when(inventoryRepository.findById(Sku.of(sku))).thenReturn(Optional.of(item));
        when(inventoryRepository.save(any())).thenReturn(item);

        InventoryItem result = inventoryService.reserveInventory(sku, quantity, tenantId);

        assertThat(result.getSku().value()).isEqualTo(sku);
        assertThat(result.getAvailableQuantity()).isEqualTo(90);
        assertThat(result.getReservedQuantity()).isEqualTo(10);
    }

    @Test
    void shouldRejectNullTenantId() {
        String sku = "SKU-123";
        int quantity = 10;

        assertThatThrownBy(() -> inventoryService.reserveInventory(sku, quantity, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tenant ID is required");
    }

    @Test
    void shouldRejectEmptyTenantId() {
        String sku = "SKU-123";
        int quantity = 10;

        assertThatThrownBy(() -> inventoryService.reserveInventory(sku, quantity, "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tenant ID is required");
    }

    @Test
    void shouldRejectInsufficientInventory() {
        String tenantId = "tenant-123";
        String sku = "SKU-123";
        int quantity = 150;

        InventoryItem item = new InventoryItem(Sku.of(sku), 100);
        when(inventoryRepository.findById(Sku.of(sku))).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> inventoryService.reserveInventory(sku, quantity, tenantId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Insufficient inventory");
    }

    @Test
    void shouldRejectInvalidSku() {
        String tenantId = "tenant-123";
        String sku = "NON-EXISTENT";
        int quantity = 10;

        when(inventoryRepository.findById(Sku.of(sku))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.reserveInventory(sku, quantity, tenantId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Inventory item not found");
    }

    @Test
    void shouldRejectZeroQuantity() {
        String tenantId = "tenant-123";
        String sku = "SKU-123";
        int quantity = 0;

        InventoryItem item = new InventoryItem(Sku.of(sku), 100);
        when(inventoryRepository.findById(Sku.of(sku))).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> inventoryService.reserveInventory(sku, quantity, tenantId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Reserve quantity must be positive");
    }

    @Test
    void shouldRejectNegativeQuantity() {
        String tenantId = "tenant-123";
        String sku = "SKU-123";
        int quantity = -5;

        InventoryItem item = new InventoryItem(Sku.of(sku), 100);
        when(inventoryRepository.findById(Sku.of(sku))).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> inventoryService.reserveInventory(sku, quantity, tenantId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Reserve quantity must be positive");
    }
}
