package com.example.margin.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import org.junit.jupiter.api.Test;

class PortfolioTest {

    private static final Currency USD = Currency.getInstance("USD");
    private static final Currency GBP = Currency.getInstance("GBP");

    private static Position position(Currency currency) {
        return new Position("AAPL", AssetClass.EQUITY, BigDecimal.TEN, BigDecimal.ONE, currency);
    }

    private static Collateral cash(Currency currency) {
        return new Collateral(CollateralType.CASH, BigDecimal.TEN, currency);
    }

    @Test
    void acceptsLinesInThePortfolioCurrency() {
        Portfolio portfolio = new Portfolio("ACC-1", USD, List.of(position(USD)), List.of(cash(USD)));

        assertThat(portfolio.currency()).isEqualTo(USD);
    }

    @Test
    void rejectsPositionInAnotherCurrency() {
        assertThatThrownBy(() -> new Portfolio("ACC-1", USD, List.of(position(GBP)), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AAPL")
                .hasMessageContaining("GBP")
                .hasMessageContaining("mixed-currency");
    }

    @Test
    void rejectsCollateralInAnotherCurrency() {
        assertThatThrownBy(() -> new Portfolio("ACC-1", USD, List.of(), List.of(cash(GBP))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CASH collateral");
    }

    @Test
    void defensivelyCopiesLines() {
        List<Position> positions = new ArrayList<>(List.of(position(USD)));
        Portfolio portfolio = new Portfolio("ACC-1", USD, positions, List.of());

        positions.clear();

        assertThat(portfolio.positions()).hasSize(1);
    }
}
