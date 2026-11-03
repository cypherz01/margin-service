package com.example.margin.api;

import com.example.margin.domain.AssetClass;
import com.example.margin.domain.Collateral;
import com.example.margin.domain.CollateralType;
import com.example.margin.domain.Portfolio;
import com.example.margin.domain.Position;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;

/**
 * Request body for POST /api/v1/margin/calculations.
 *
 * <p>Every field is bounded. Unbounded {@link BigDecimal} input is a denial-of-service risk: a 9-character
 * value such as {@code 1e5000000} parses cheaply but costs seconds of CPU once it is multiplied and rounded.
 * {@code @Digits} rejects such values before they reach the calculator, and the list limits cap request cost.
 *
 * <p>{@code currency} is the portfolio's ISO 4217 reporting currency. Positions and collateral may state their own
 * currency; when they omit it they inherit the portfolio's. A line in any other currency is rejected (400), as the
 * service does no FX conversion.
 */
public record MarginRequest(
        @NotBlank
        @Pattern(regexp = MarginRequest.ACCOUNT_ID_PATTERN,
                message = "must be 1-64 letters, digits, '.', '_', ':' or '-'")
        String accountId,
        @NotNull Currency currency,
        @NotNull @Size(max = MarginRequest.MAX_POSITIONS) List<@Valid @NotNull PositionDto> positions,
        @NotNull @Size(max = MarginRequest.MAX_COLLATERAL) List<@Valid @NotNull CollateralDto> collateral) {

    static final String ACCOUNT_ID_PATTERN = "[A-Za-z0-9._:-]{1,64}";
    static final int MAX_POSITIONS = 1_000;
    static final int MAX_COLLATERAL = 100;

    /**
     * @param quantity signed (negative = short); at most 12 integer and 6 fractional digits
     * @param price    non-negative; at most 12 integer and 8 fractional digits
     * @param currency optional; defaults to the portfolio currency
     */
    public record PositionDto(
            @NotBlank @Size(max = 64) String instrumentId,
            @NotNull AssetClass assetClass,
            @NotNull @Digits(integer = 12, fraction = 6) BigDecimal quantity,
            @NotNull @PositiveOrZero @Digits(integer = 12, fraction = 8) BigDecimal price,
            Currency currency) {
    }

    /**
     * @param marketValue non-negative; at most 15 integer and 2 fractional digits
     * @param currency    optional; defaults to the portfolio currency
     */
    public record CollateralDto(
            @NotNull CollateralType type,
            @NotNull @PositiveOrZero @Digits(integer = 15, fraction = 2) BigDecimal marketValue,
            Currency currency) {
    }

    public Portfolio toPortfolio() {
        return new Portfolio(
                accountId,
                currency,
                positions.stream()
                        .map(p -> new Position(p.instrumentId(), p.assetClass(), p.quantity(), p.price(),
                                orPortfolioCurrency(p.currency())))
                        .toList(),
                collateral.stream()
                        .map(c -> new Collateral(c.type(), c.marketValue(), orPortfolioCurrency(c.currency())))
                        .toList());
    }

    private Currency orPortfolioCurrency(Currency lineCurrency) {
        return lineCurrency != null ? lineCurrency : currency;
    }
}
