package com.example.margin.api;

import com.example.margin.marketdata.Quote;
import com.example.margin.marketdata.QuoteProvider;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/quotes")
public class QuoteController {

    private final QuoteProvider quoteProvider;

    public QuoteController(QuoteProvider quoteProvider) {
        this.quoteProvider = quoteProvider;
    }

    /** Latest price for a ticker symbol (e.g. AAPL, BRK.B), used to fill in position prices. */
    @GetMapping("/{symbol}")
    public Quote latest(@PathVariable @Pattern(regexp = "[A-Za-z0-9.\\-]{1,15}") String symbol) {
        return quoteProvider.latest(symbol);
    }
}
