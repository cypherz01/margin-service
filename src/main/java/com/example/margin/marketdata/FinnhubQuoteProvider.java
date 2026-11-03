package com.example.margin.marketdata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.Locale;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Looks up the latest price via Finnhub's {@code GET /quote} endpoint.
 * The API key is sent in a header rather than the query string so it never appears in URLs or logs.
 */
public class FinnhubQuoteProvider implements QuoteProvider {

    private static final String TOKEN_HEADER = "X-Finnhub-Token";

    /** Finnhub's quote endpoint covers US listings (non-US exchanges need a paid plan), all priced in US dollars. */
    private static final Currency QUOTE_CURRENCY = Currency.getInstance("USD");

    private final RestClient restClient;
    private final String apiKey;

    public FinnhubQuoteProvider(RestClient restClient, String apiKey) {
        this.restClient = restClient;
        this.apiKey = apiKey;
    }

    @Override
    public Quote latest(String symbol) {
        String normalised = symbol.trim().toUpperCase(Locale.ROOT);
        if (apiKey == null || apiKey.isBlank()) {
            throw new MarketDataUnavailableException("Finnhub API key not configured; set FINNHUB_API_KEY", null);
        }

        FinnhubQuote body;
        try {
            body = restClient.get()
                    .uri("/quote?symbol={symbol}", normalised)
                    .header(TOKEN_HEADER, apiKey)
                    .retrieve()
                    .body(FinnhubQuote.class);
        } catch (RestClientException ex) {
            throw new MarketDataUnavailableException("Finnhub quote request failed for " + normalised, ex);
        }

        // Finnhub returns 200 with zeroed fields for symbols it doesn't recognise
        if (body == null || body.c() == null || body.c().signum() == 0 || body.t() == null || body.t() == 0) {
            throw new QuoteNotFoundException(normalised);
        }
        return new Quote(normalised, body.c(), QUOTE_CURRENCY, Instant.ofEpochSecond(body.t()));
    }

    /** Subset of Finnhub's quote payload: c = current price, t = quote time (epoch seconds). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record FinnhubQuote(BigDecimal c, Long t) {
    }
}
