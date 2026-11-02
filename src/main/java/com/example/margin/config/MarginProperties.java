package com.example.margin.config;

import com.example.margin.domain.AssetClass;
import com.example.margin.domain.CollateralType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Margin model parameters, bound from {@code margin.*} in application.yml.
 *
 * @param initialMarginRates initial margin as a fraction of exposure, per asset class
 * @param maintenanceRatio   maintenance margin as a fraction of initial margin
 * @param haircuts           fraction deducted from collateral market value, per collateral type
 * @param marginCallTopic    Kafka topic that margin-call events are published to
 * @param publishTimeout     how long to wait for the broker to acknowledge a margin-call event
 */
@Validated
@ConfigurationProperties(prefix = "margin")
public record MarginProperties(
        @NotEmpty Map<AssetClass, BigDecimal> initialMarginRates,
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal maintenanceRatio,
        @NotEmpty Map<CollateralType, BigDecimal> haircuts,
        @NotBlank String marginCallTopic,
        @NotNull Duration publishTimeout) {
}
