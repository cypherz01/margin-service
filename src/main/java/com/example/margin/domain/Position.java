package com.example.margin.domain;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

/**
 * A holding in a portfolio. Quantity is signed: positive = long, negative = short.
 *
 * @param currency currency the price is quoted in
 */
public record Position(
        String instrumentId, AssetClass assetClass, BigDecimal quantity, BigDecimal price, Currency currency) {

    public Position {
        Objects.requireNonNull(instrumentId, "instrumentId");
        Objects.requireNonNull(assetClass, "assetClass");
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(currency, "currency");
        if (price.signum() < 0) {
            throw new IllegalArgumentException("price must not be negative");
        }
    }

    /** Absolute market value (exposure) of the position, in {@link #currency()}. */
    public BigDecimal exposure() {
        return quantity.multiply(price).abs();
    }
}
