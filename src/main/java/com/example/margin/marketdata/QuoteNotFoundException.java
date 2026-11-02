package com.example.margin.marketdata;

/** The market-data provider has no price for the requested symbol. */
public class QuoteNotFoundException extends RuntimeException {

    public QuoteNotFoundException(String symbol) {
        super("No quote found for symbol " + symbol);
    }
}
