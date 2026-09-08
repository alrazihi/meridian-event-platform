package com.meridian.event.domain.model.valueobjects;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkuTest {

    @Test
    void shouldCreateSkuFromValidString() {
        Sku sku = Sku.of("SKU-123");

        assertThat(sku.value()).isEqualTo("SKU-123");
    }

    @Test
    void shouldTrimWhitespace() {
        Sku sku = Sku.of("  SKU-123  ");

        assertThat(sku.value()).isEqualTo("SKU-123");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t", "\n"})
    void shouldFailForBlankSku(String blank) {
        assertThatThrownBy(() -> Sku.of(blank))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SKU cannot be blank");
    }

    @Test
    void shouldFailForNullSku() {
        assertThatThrownBy(() -> Sku.of(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SKU cannot be null");
    }

    @Test
    void shouldCheckEquality() {
        Sku sku1 = Sku.of("SKU-123");
        Sku sku2 = Sku.of("SKU-123");
        Sku sku3 = Sku.of("SKU-456");

        assertThat(sku1).isEqualTo(sku2);
        assertThat(sku1).isNotEqualTo(sku3);
        assertThat(sku1.hashCode()).isEqualTo(sku2.hashCode());
    }

    @Test
    void shouldFormatToString() {
        Sku sku = Sku.of("SKU-123");

        assertThat(sku.toString()).isEqualTo("SKU-123");
    }
}