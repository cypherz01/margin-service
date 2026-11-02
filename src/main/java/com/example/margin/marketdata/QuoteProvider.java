package com.example.margin.marketdata;

/** Port for looking up live prices; decouples the API from a specific market-data vendor. */
public interface QuoteProvider {

    /**
     * @throws QuoteNotFoundException         if the provider has no price for the symbol
     * @throws MarketDataUnavailableException if the provider cannot be reached or refuses the request
     */
    Quote latest(String symbol);
}
