package com.meridian.event.infrastructure.persistence.adapter;

import com.meridian.event.application.port.outbound.OrderRepository;
import com.meridian.event.domain.model.Order;
import com.meridian.event.domain.model.OrderLine;
import com.meridian.event.domain.model.valueobjects.Money;
import com.meridian.event.domain.model.valueobjects.OrderId;
import com.meridian.event.domain.model.valueobjects.Sku;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@Import(RepositoryTestConfig.class)
@Transactional
class OrderRepositoryAdapterIntegrationTest {

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void shouldSaveAndFindOrder() {
        OrderId orderId = OrderId.generate();
        List<OrderLine> lines = List.of(
                new OrderLine(Sku.of("SKU-1"), 2, Money.of(new BigDecimal("10.00"), "USD")),
                new OrderLine(Sku.of("SKU-2"), 1, Money.of(new BigDecimal("25.00"), "USD"))
        );
        Order order = new Order(orderId, "customer-123", lines);

        Order saved = orderRepository.save(order);

        assertThat(saved.getId()).isEqualTo(orderId);
        assertThat(saved.getTotal().value()).isEqualByComparingTo("45.00");
        assertThat(saved.getLines()).hasSize(2);

        Optional<Order> found = orderRepository.findById(orderId);
        assertThat(found).isPresent();
        assertThat(found.get().getCustomerId()).isEqualTo("customer-123");
        assertThat(found.get().getTotal().value()).isEqualByComparingTo("45.00");
    }

    @Test
    void shouldUpdateExistingOrder() {
        Order order = new Order(OrderId.generate(), "customer-123", List.of(
                new OrderLine(Sku.of("SKU-1"), 1, Money.of(new BigDecimal("10.00"), "USD"))
        ));
        Order saved = orderRepository.save(order);

        saved.confirm();
        Order updated = orderRepository.save(saved);

        assertThat(updated.getStatus()).isEqualTo(com.meridian.event.domain.model.OrderStatus.CONFIRMED);
        assertThat(updated.getVersion()).isEqualTo(1L);

        Optional<Order> found = orderRepository.findById(saved.getId());
        assertThat(found.get().getStatus()).isEqualTo(com.meridian.event.domain.model.OrderStatus.CONFIRMED);
    }

    @Test
    void shouldReturnEmptyForNonExistentOrder() {
        Optional<Order> found = orderRepository.findById(OrderId.from(UUID.randomUUID().toString()));
        assertThat(found).isEmpty();
    }

    @Test
    void shouldPersistOrderLines() {
        Order order = new Order(OrderId.generate(), "customer-123", List.of(
                new OrderLine(Sku.of("SKU-1"), 2, Money.of(new BigDecimal("10.00"), "USD")),
                new OrderLine(Sku.of("SKU-2"), 3, Money.of(new BigDecimal("15.00"), "USD"))
        ));

        Order saved = orderRepository.save(order);
        Optional<Order> found = orderRepository.findById(saved.getId());

        assertThat(found.get().getLines()).hasSize(2);
        assertThat(found.get().getLines().get(0).getSku().value()).isEqualTo("SKU-1");
        assertThat(found.get().getLines().get(0).getQuantity()).isEqualTo(2);
        assertThat(found.get().getLines().get(1).getSku().value()).isEqualTo("SKU-2");
        assertThat(found.get().getLines().get(1).getQuantity()).isEqualTo(3);
    }
}
