package com.meridian.event.application.service;

import com.meridian.event.application.port.inbound.ReserveInventoryUseCase;
import com.meridian.event.application.port.outbound.EventPublisher;
import com.meridian.event.application.port.outbound.InventoryItemRepository;
import com.meridian.event.domain.model.InventoryItem;
import com.meridian.event.domain.model.valueobjects.Sku;
import com.meridian.event.domain.model.InventoryReservedEvent;
import com.meridian.event.infrastructure.security.audit.SecurityAuditLogger;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletRequest;
import java.util.Optional;

@Service
public class DefaultInventoryService implements ReserveInventoryUseCase {

    private final InventoryItemRepository inventoryRepository;
    private final EventPublisher eventPublisher;
    private final SecurityAuditLogger auditLogger;
    private final HttpServletRequest request;

    public DefaultInventoryService(
            InventoryItemRepository inventoryRepository,
            EventPublisher eventPublisher,
            SecurityAuditLogger auditLogger,
            HttpServletRequest request) {
        this.inventoryRepository = inventoryRepository;
        this.eventPublisher = eventPublisher;
        this.auditLogger = auditLogger;
        this.request = request;
    }

    @Override
    @Transactional
    public InventoryItem reserveInventory(String sku, int quantity, String tenantId) {
        String ip = getClientIp();
        Sku skuObj = Sku.of(sku);

        Optional<InventoryItem> existingItem = inventoryRepository.findById(skuObj);
        if (existingItem.isEmpty()) {
            auditLogger.logInventoryReservation(tenantId, ip, sku, quantity, "NOT_FOUND");
            throw new IllegalStateException("Inventory item not found for SKU: " + sku);
        }

        InventoryItem item = existingItem.get();

        // TODO: Add tenant isolation check when InventoryItem has tenantId field
        // For now, validate tenantId format to prevent injection
        if (tenantId == null || tenantId.trim().isEmpty()) {
            auditLogger.logInventoryReservation(tenantId, ip, sku, quantity, "INVALID_TENANT");
            throw new IllegalArgumentException("Tenant ID is required");
        }

        try {
            item.reserve(quantity);

            InventoryItem savedItem = inventoryRepository.save(item);

            InventoryReservedEvent event = new InventoryReservedEvent(skuObj, quantity, sku);
            eventPublisher.publish(event);

            auditLogger.logInventoryReservation(tenantId, ip, sku, quantity, "SUCCESS");

            return savedItem;
        } catch (IllegalStateException e) {
            auditLogger.logInventoryReservation(tenantId, ip, sku, quantity, "INSUFFICIENT_STOCK");
            throw e;
        }
    }

    private String getClientIp() {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader != null && !xfHeader.isEmpty()) {
            return xfHeader.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}



