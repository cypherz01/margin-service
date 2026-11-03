package com.example.margin.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;

/** Event published to Kafka when a portfolio breaches its maintenance margin. */
public record MarginCallEvent(
        String eventId,
        String accountId,
        Currency currency,
        BigDecimal callAmount,
        BigDecimal initialMargin,
        BigDecimal maintenanceMargin,
        BigDecimal collateralValue,
        Instant issuedAt) {
}
