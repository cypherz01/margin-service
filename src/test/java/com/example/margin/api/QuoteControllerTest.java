package com.example.margin.api;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.margin.marketdata.MarketDataUnavailableException;
import com.example.margin.marketdata.Quote;
import com.example.margin.marketdata.QuoteNotFoundException;
import com.example.margin.marketdata.QuoteProvider;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(QuoteController.class)
class QuoteControllerTest {

    private static final String URL = "/api/v1/quotes/{symbol}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private QuoteProvider quoteProvider;

    @Test
    void returnsLatestQuote() throws Exception {
        when(quoteProvider.latest("AAPL")).thenReturn(
                new Quote("AAPL", new BigDecimal("189.84"), Instant.parse("2025-11-02T16:00:00Z")));

        mockMvc.perform(get(URL, "AAPL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.price").value(189.84))
                .andExpect(jsonPath("$.asOf").value("2025-11-02T16:00:00Z"));
    }

    @Test
    void unknownSymbolIs404Problem() throws Exception {
        when(quoteProvider.latest("NOPE")).thenThrow(new QuoteNotFoundException("NOPE"));

        mockMvc.perform(get(URL, "NOPE"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("No quote found for symbol NOPE"));
    }

    @Test
    void providerOutageIs503Problem() throws Exception {
        when(quoteProvider.latest(anyString()))
                .thenThrow(new MarketDataUnavailableException("Finnhub returned 429", null));

        mockMvc.perform(get(URL, "AAPL"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(503));
    }

    @Test
    void rejectsMalformedSymbolWithoutCallingProvider() throws Exception {
        mockMvc.perform(get(URL, "<script>"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(quoteProvider);
    }
}
