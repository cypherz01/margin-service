package com.example.margin.domain;

import java.util.Currency;
import java.util.List;
import java.util.Objects;

/**
 * An account's positions together with the collateral posted against them.
 *
 * <p>All amounts must be in the portfolio's {@code currency}. Mixing currencies would silently add, say,
 * dollars to yen, so it is rejected here rather than in each caller; supporting it needs explicit FX
 * conversion at an agreed rate, which this service does not (yet) do.
 *
 * @param currency the account's reporting currency; every position and collateral item must match it
 */
public record Portfolio(String accountId, Currency currency, List<Position> positions, List<Collateral> collateral) {

    public Portfolio {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(currency, "currency");
        positions = List.copyOf(positions);
        collateral = List.copyOf(collateral);

        for (Position position : positions) {
            requireSameCurrency(currency, position.currency(), "Position " + position.instrumentId());
        }
        for (Collateral item : collateral) {
            requireSameCurrency(currency, item.currency(), item.type() + " collateral");
        }
    }

    private static void requireSameCurrency(Currency expected, Currency actual, String what) {
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException(what + " is in " + actual + " but the portfolio currency is "
                    + expected + "; mixed-currency portfolios are not supported");
        }
    }
}
