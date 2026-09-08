package com.meridian.event.domain.model.valueobjects;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentIdTest {

    @Test
    void shouldGenerateUniqueIds() {
        PaymentId id1 = PaymentId.generate();
        PaymentId id2 = PaymentId.generate();

        assertThat(id1).isNotEqualTo(id2);
        assertThat(id1.value()).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    void shouldCreateFromValidString() {
        String uuid = UUID.randomUUID().toString();
        PaymentId id = PaymentId.from(uuid);

        assertThat(id.value()).isEqualTo(uuid);
    }

    @Test
    void shouldFailToCreateFromNull() {
        assertThatThrownBy(() -> PaymentId.from(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PaymentId cannot be null");
    }

    @Test
    void shouldFailToCreateFromInvalidFormat() {
        assertThatThrownBy(() -> PaymentId.from("not-a-uuid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid PaymentId format");
    }

    @Test
    void shouldCheckEquality() {
        String uuid = UUID.randomUUID().toString();
        PaymentId id1 = PaymentId.from(uuid);
        PaymentId id2 = PaymentId.from(uuid);
        PaymentId id3 = PaymentId.generate();

        assertThat(id1).isEqualTo(id2);
        assertThat(id1).isNotEqualTo(id3);
    }
}