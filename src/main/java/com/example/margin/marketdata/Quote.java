package com.example.margin.marketdata;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;

/**
 * Latest traded price for a listed instrument.
 *
 * @param currency currency the price is quoted in; callers must not use the price in a portfolio of another currency
 */
public record Quote(String symbol, BigDecimal price, Currency currency, Instant asOf) {
}
