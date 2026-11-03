package com.example.margin.domain;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

/**
 * Collateral posted against a portfolio, at market value before haircut.
 *
 * @param currency currency the market value is expressed in
 */
public record Collateral(CollateralType type, BigDecimal marketValue, Currency currency) {

    public Collateral {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(marketValue, "marketValue");
        Objects.requireNonNull(currency, "currency");
        if (marketValue.signum() < 0) {
            throw new IllegalArgumentException("marketValue must not be negative");
        }
    }
}
