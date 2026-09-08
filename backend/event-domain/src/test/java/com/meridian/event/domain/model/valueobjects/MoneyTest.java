package com.meridian.event.domain.model.valueobjects;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void shouldCreateMoneyWithAmountAndCurrency() {
        Money money = Money.of(new BigDecimal("100.50"), "USD");

        assertThat(money.value()).isEqualByComparingTo("100.50");
        assertThat(money.currency()).isEqualTo(Currency.getInstance("USD"));
    }

    @Test
    void shouldCreateZeroMoney() {
        Money zero = Money.zero();

        assertThat(zero.value()).isEqualByComparingTo("0.00");
        assertThat(zero.currency()).isEqualTo(Currency.getInstance("USD"));
    }

    @Test
    void shouldAddMoney() {
        Money a = Money.of(new BigDecimal("100.00"), "USD");
        Money b = Money.of(new BigDecimal("50.50"), "USD");

        Money sum = a.add(b);

        assertThat(sum.value()).isEqualByComparingTo("150.50");
    }

    @Test
    void shouldFailToAddDifferentCurrencies() {
        Money usd = Money.of(new BigDecimal("100.00"), "USD");
        Money eur = Money.of(new BigDecimal("50.00"), "EUR");

        assertThatThrownBy(() -> usd.add(eur))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Currency mismatch");
    }

    @Test
    void shouldSubtractMoney() {
        Money a = Money.of(new BigDecimal("100.00"), "USD");
        Money b = Money.of(new BigDecimal("30.00"), "USD");

        Money diff = a.subtract(b);

        assertThat(diff.value()).isEqualByComparingTo("70.00");
    }

    @Test
    void shouldFailToSubtractDifferentCurrencies() {
        Money usd = Money.of(new BigDecimal("100.00"), "USD");
        Money eur = Money.of(new BigDecimal("50.00"), "EUR");

        assertThatThrownBy(() -> usd.subtract(eur))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Currency mismatch");
    }

    @Test
    void shouldMultiplyMoney() {
        Money money = Money.of(new BigDecimal("10.00"), "USD");

        Money multiplied = money.multiply(3);

        assertThat(multiplied.value()).isEqualByComparingTo("30.00");
    }

    @Test
    void shouldCompareMoney() {
        Money a = Money.of(new BigDecimal("100.00"), "USD");
        Money b = Money.of(new BigDecimal("50.00"), "USD");
        Money c = Money.of(new BigDecimal("100.00"), "USD");

        assertThat(a.compareTo(b)).isPositive();
        assertThat(b.compareTo(a)).isNegative();
        assertThat(a.compareTo(c)).isZero();
    }

    @Test
    void shouldFailToCompareDifferentCurrencies() {
        Money usd = Money.of(new BigDecimal("100.00"), "USD");
        Money eur = Money.of(new BigDecimal("50.00"), "EUR");

        assertThatThrownBy(() -> usd.compareTo(eur))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Currency mismatch");
    }

    @Test
    void shouldCheckEquality() {
        Money a = Money.of(new BigDecimal("100.00"), "USD");
        Money b = Money.of(new BigDecimal("100.00"), "USD");
        Money c = Money.of(new BigDecimal("50.00"), "USD");
        Money d = Money.of(new BigDecimal("100.00"), "EUR");

        assertThat(a).isEqualTo(b);
        assertThat(a).isNotEqualTo(c);
        assertThat(a).isNotEqualTo(d);
    }

    @Test
    void shouldFormatToString() {
        Money money = Money.of(new BigDecimal("100.50"), "USD");

        assertThat(money.toString()).isEqualTo("USD 100.50");
    }

    @ParameterizedTest
    @MethodSource("invalidMoneyArguments")
    void shouldFailToCreateWithInvalidData(BigDecimal amount, String currency, String expectedError) {
        assertThatThrownBy(() -> Money.of(amount, currency))
                .hasMessageContaining(expectedError);
    }

    static Stream<Arguments> invalidMoneyArguments() {
        return Stream.of(
                Arguments.of(null, "USD", "amount cannot be null"),
                Arguments.of(new BigDecimal("100.00"), null, "currency cannot be null"),
                Arguments.of(new BigDecimal("100.00"), "", "currency cannot be null"),
                Arguments.of(new BigDecimal("100.00"), "INVALID", "Invalid currency")
        );
    }
}