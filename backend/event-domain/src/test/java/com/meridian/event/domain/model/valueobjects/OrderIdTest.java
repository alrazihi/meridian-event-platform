package com.meridian.event.domain.model.valueobjects;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderIdTest {

    @Test
    void shouldGenerateUniqueIds() {
        OrderId id1 = OrderId.generate();
        OrderId id2 = OrderId.generate();

        assertThat(id1).isNotEqualTo(id2);
        assertThat(id1.value()).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    void shouldCreateFromValidString() {
        String uuid = UUID.randomUUID().toString();
        OrderId id = OrderId.from(uuid);

        assertThat(id.value()).isEqualTo(uuid);
    }

    @Test
    void shouldFailToCreateFromNull() {
        assertThatThrownBy(() -> OrderId.from(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OrderId cannot be null");
    }

    @Test
    void shouldFailToCreateFromInvalidFormat() {
        assertThatThrownBy(() -> OrderId.from("not-a-uuid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid OrderId format");
    }

    @Test
    void shouldCheckEquality() {
        String uuid = UUID.randomUUID().toString();
        OrderId id1 = OrderId.from(uuid);
        OrderId id2 = OrderId.from(uuid);
        OrderId id3 = OrderId.generate();

        assertThat(id1).isEqualTo(id2);
        assertThat(id1).isNotEqualTo(id3);
        assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
    }

    @Test
    void shouldFormatToString() {
        String uuid = "550e8400-e29b-41d4-a716-446655440000";
        OrderId id = OrderId.from(uuid);

        assertThat(id.toString()).isEqualTo(uuid);
    }
}