package com.example.margin.api;

import com.example.margin.marketdata.Quote;
import com.example.margin.marketdata.QuoteProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/quotes")
@Tag(name = "Market data")
public class QuoteController {

    private static final String PROBLEM_JSON = "application/problem+json";

    private final QuoteProvider quoteProvider;

    public QuoteController(QuoteProvider quoteProvider) {
        this.quoteProvider = quoteProvider;
    }

    /** Latest price for a ticker symbol (e.g. AAPL, BRK.B), used to fill in position prices. */
    @Operation(summary = "Latest share price", description = "Latest traded price for a ticker, via Finnhub.")
    @ApiResponse(responseCode = "200", description = "Latest price and its timestamp")
    @ApiResponse(responseCode = "400", description = "Symbol is not 1-15 letters, digits, '.' or '-'",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "The provider does not know the symbol",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "503", description = "Provider unreachable, rate-limited or not configured",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/{symbol}")
    public Quote latest(
            @Parameter(description = "Ticker symbol", example = "AAPL")
            @PathVariable @Pattern(regexp = "[A-Za-z0-9.\\-]{1,15}") String symbol) {
        return quoteProvider.latest(symbol);
    }
}
