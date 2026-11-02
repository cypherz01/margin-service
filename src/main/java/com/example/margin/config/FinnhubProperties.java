package com.example.margin.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Finnhub market-data settings, bound from {@code market-data.finnhub.*} in application.yml.
 *
 * @param baseUrl Finnhub REST API root
 * @param apiKey  API key; may be blank, in which case price lookups report market data as unavailable
 * @param timeout connect and read timeout for quote requests
 */
@Validated
@ConfigurationProperties(prefix = "market-data.finnhub")
public record FinnhubProperties(
        @NotBlank String baseUrl,
        String apiKey,
        @NotNull Duration timeout) {
}
