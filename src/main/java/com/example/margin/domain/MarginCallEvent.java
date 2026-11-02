package com.example.margin.domain;

import java.math.BigDecimal;
import java.time.Instant;

/** Event published to Kafka when a portfolio breaches its maintenance margin. */
public record MarginCallEvent(
        String eventId,
        String accountId,
        BigDecimal callAmount,
        BigDecimal initialMargin,
        BigDecimal maintenanceMargin,
        BigDecimal collateralValue,
        Instant issuedAt) {
}
