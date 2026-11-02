package com.example.margin.api;

import com.example.margin.domain.AssetClass;
import com.example.margin.domain.Collateral;
import com.example.margin.domain.CollateralType;
import com.example.margin.domain.Portfolio;
import com.example.margin.domain.Position;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.List;

/** Request body for POST /api/v1/margin/calculations. */
public record MarginRequest(
        @NotBlank String accountId,
        @NotNull List<@Valid @NotNull PositionDto> positions,
        @NotNull List<@Valid @NotNull CollateralDto> collateral) {

    public record PositionDto(
            @NotBlank String instrumentId,
            @NotNull AssetClass assetClass,
            @NotNull BigDecimal quantity,
            @NotNull @PositiveOrZero BigDecimal price) {
    }

    public record CollateralDto(
            @NotNull CollateralType type,
            @NotNull @PositiveOrZero BigDecimal marketValue) {
    }

    public Portfolio toPortfolio() {
        return new Portfolio(
                accountId,
                positions.stream()
                        .map(p -> new Position(p.instrumentId(), p.assetClass(), p.quantity(), p.price()))
                        .toList(),
                collateral.stream()
                        .map(c -> new Collateral(c.type(), c.marketValue()))
                        .toList());
    }
}
