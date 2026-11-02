package com.example.margin.marketdata;

/** The market-data provider could not be reached, rejected our credentials, or rate-limited us. */
public class MarketDataUnavailableException extends RuntimeException {

    public MarketDataUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
