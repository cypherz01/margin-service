package com.example.margin.domain;

import java.math.BigDecimal;
import java.util.Objects;

/** Collateral posted against a portfolio, at market value before haircut. */
public record Collateral(CollateralType type, BigDecimal marketValue) {

    public Collateral {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(marketValue, "marketValue");
        if (marketValue.signum() < 0) {
            throw new IllegalArgumentException("marketValue must not be negative");
        }
    }
}
