package com.meridian.event.infrastructure.persistence.mapper;

import com.meridian.event.domain.model.Order;
import com.meridian.event.domain.model.OrderLine;
import com.meridian.event.domain.model.valueobjects.Money;
import com.meridian.event.domain.model.valueobjects.OrderId;
import com.meridian.event.domain.model.valueobjects.Sku;
import com.meridian.event.infrastructure.persistence.jpa.OrderEntity;
import com.meridian.event.infrastructure.persistence.jpa.OrderLineEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class OrderMapper {

    public static OrderEntity toEntity(Order order) {
        OrderEntity entity = new OrderEntity();
        entity.setId(order.getId().value());
        entity.setCustomerId(order.getCustomerId());
        entity.setTotal(order.getTotal().value());
        entity.setStatus(order.getStatus().name());
        entity.setVersion(order.getVersion());
        entity.setCreatedAt(order.getCreatedAt());
        entity.setUpdatedAt(order.getUpdatedAt());

        List<OrderLineEntity> lineEntities = order.getLines().stream()
                .map(line -> toLineEntity(line, entity))
                .toList();
        entity.setLines(lineEntities);

        return entity;
    }

    private static OrderLineEntity toLineEntity(OrderLine line, OrderEntity orderEntity) {
        OrderLineEntity lineEntity = new OrderLineEntity();
        lineEntity.setOrder(orderEntity);
        lineEntity.setSku(line.getSku().value());
        lineEntity.setQuantity(line.getQuantity());
        lineEntity.setUnitPrice(line.getUnitPrice().value());
        return lineEntity;
    }

    public static Order toDomain(OrderEntity entity) {
        List<OrderLine> lines = entity.getLines().stream()
                .map(OrderMapper::toDomainLine)
                .toList();

        Order order = new Order(
                OrderId.from(entity.getId()),
                entity.getCustomerId(),
                lines
        );

        order.setTotal(Money.of(entity.getTotal(), "USD"));
        order.setStatus(com.meridian.event.domain.model.OrderStatus.valueOf(entity.getStatus()));
        order.setVersion(entity.getVersion());
        order.setCreatedAt(entity.getCreatedAt());
        order.setUpdatedAt(entity.getUpdatedAt());
        return order;
    }

    private static OrderLine toDomainLine(OrderLineEntity lineEntity) {
        return new OrderLine(
                Sku.of(lineEntity.getSku()),
                lineEntity.getQuantity(),
                Money.of(lineEntity.getUnitPrice(), "USD")
        );
    }
}