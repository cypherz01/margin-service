package com.example.margin.domain;

import java.util.List;
import java.util.Objects;

/** An account's positions together with the collateral posted against them. */
public record Portfolio(String accountId, List<Position> positions, List<Collateral> collateral) {

    public Portfolio {
        Objects.requireNonNull(accountId, "accountId");
        positions = List.copyOf(positions);
        collateral = List.copyOf(collateral);
    }
}
