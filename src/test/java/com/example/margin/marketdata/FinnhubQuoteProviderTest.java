package com.example.margin.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

import java.net.SocketTimeoutException;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseActions;
import org.springframework.web.client.RestClient;

class FinnhubQuoteProviderTest {

    private static final String BASE_URL = "https://finnhub.test/api/v1";

    private MockRestServiceServer server;
    private FinnhubQuoteProvider provider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new FinnhubQuoteProvider(builder.build(), "test-key");
    }

    /** The key goes in a header, never the URL, so it can't leak into access logs. */
    private ResponseActions expectQuoteRequest(String symbol) {
        return server.expect(requestTo(BASE_URL + "/quote?symbol=" + symbol))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Finnhub-Token", "test-key"));
    }

    @Test
    void returnsCurrentPriceAndQuoteTime() {
        expectQuoteRequest("AAPL").andRespond(withSuccess("""
                {"c":189.84,"d":1.2,"dp":0.64,"h":190.5,"l":188.1,"o":188.9,"pc":188.64,"t":1762099200}
                """, MediaType.APPLICATION_JSON));

        Quote quote = provider.latest("AAPL");

        assertThat(quote.symbol()).isEqualTo("AAPL");
        assertThat(quote.price()).isEqualByComparingTo("189.84");
        assertThat(quote.asOf()).isEqualTo(Instant.ofEpochSecond(1762099200));
        server.verify();
    }

    @Test
    void normalisesSymbolToUpperCase() {
        expectQuoteRequest("MSFT").andRespond(withSuccess("""
                {"c":420.5,"t":1762099200}
                """, MediaType.APPLICATION_JSON));

        assertThat(provider.latest(" msft ").symbol()).isEqualTo("MSFT");
        server.verify();
    }

    /** Finnhub answers 200 with all-zero fields for symbols it doesn't know. */
    @Test
    void unknownSymbolIsNotFound() {
        expectQuoteRequest("NOPE").andRespond(withSuccess("""
                {"c":0,"d":null,"dp":null,"h":0,"l":0,"o":0,"pc":0,"t":0}
                """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.latest("NOPE"))
                .isInstanceOf(QuoteNotFoundException.class)
                .hasMessageContaining("NOPE");
    }

    @Test
    void rejectedApiKeyMeansMarketDataUnavailable() {
        expectQuoteRequest("AAPL").andRespond(withUnauthorizedRequest());

        assertThatThrownBy(() -> provider.latest("AAPL")).isInstanceOf(MarketDataUnavailableException.class);
    }

    @Test
    void rateLimitMeansMarketDataUnavailable() {
        expectQuoteRequest("AAPL").andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> provider.latest("AAPL")).isInstanceOf(MarketDataUnavailableException.class);
    }

    @Test
    void providerErrorMeansMarketDataUnavailable() {
        expectQuoteRequest("AAPL").andRespond(withServerError());

        assertThatThrownBy(() -> provider.latest("AAPL")).isInstanceOf(MarketDataUnavailableException.class);
    }

    @Test
    void networkFailureMeansMarketDataUnavailable() {
        expectQuoteRequest("AAPL").andRespond(withException(new SocketTimeoutException("read timed out")));

        assertThatThrownBy(() -> provider.latest("AAPL"))
                .isInstanceOf(MarketDataUnavailableException.class)
                .hasRootCauseInstanceOf(SocketTimeoutException.class);
    }

    @Test
    void missingApiKeyFailsWithoutCallingFinnhub() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer untouched = MockRestServiceServer.bindTo(builder).build();
        FinnhubQuoteProvider unconfigured = new FinnhubQuoteProvider(builder.build(), "");

        assertThatThrownBy(() -> unconfigured.latest("AAPL"))
                .isInstanceOf(MarketDataUnavailableException.class)
                .hasMessageContaining("FINNHUB_API_KEY");
        untouched.verify();
    }
}
