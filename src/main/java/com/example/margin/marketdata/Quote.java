package com.example.margin.marketdata;

import java.math.BigDecimal;
import java.time.Instant;

/** Latest traded price for a listed instrument. */
public record Quote(String symbol, BigDecimal price, Instant asOf) {
}
