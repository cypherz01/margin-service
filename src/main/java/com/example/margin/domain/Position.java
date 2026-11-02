package com.example.margin.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * A holding in a portfolio. Quantity is signed: positive = long, negative = short.
 */
public record Position(String instrumentId, AssetClass assetClass, BigDecimal quantity, BigDecimal price) {

    public Position {
        Objects.requireNonNull(instrumentId, "instrumentId");
        Objects.requireNonNull(assetClass, "assetClass");
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(price, "price");
        if (price.signum() < 0) {
            throw new IllegalArgumentException("price must not be negative");
        }
    }

    /** Absolute market value (exposure) of the position. */
    public BigDecimal exposure() {
        return quantity.multiply(price).abs();
    }
}
