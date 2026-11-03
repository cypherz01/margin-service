package com.example.margin.calculation;

import com.example.margin.config.MarginProperties;
import com.example.margin.domain.Collateral;
import com.example.margin.domain.MarginResult;
import com.example.margin.domain.Portfolio;
import com.example.margin.domain.Position;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import org.springframework.stereotype.Component;

/**
 * Pure, stateless margin model.
 *
 * <ul>
 *   <li>Initial margin = sum over positions of |quantity x price| x asset-class rate.</li>
 *   <li>Maintenance margin = initial margin x maintenance ratio.</li>
 *   <li>Collateral value = sum of market value x (1 - haircut).</li>
 *   <li>A margin call is required when collateral value is strictly below maintenance margin;
 *       the call amount restores collateral to the initial margin.</li>
 * </ul>
 *
 * <p>Results are rounded half-even (banker's rounding) to the currency's minor unit: 2 decimals for USD,
 * 0 for JPY, 3 for KWD. Intermediate sums keep full precision so rounding is applied once, not per position.
 */
@Component
public class MarginCalculator {

    /** Used for currencies with no ISO minor unit, such as XAU (gold). */
    private static final int DEFAULT_SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;

    private final MarginProperties properties;

    public MarginCalculator(MarginProperties properties) {
        this.properties = properties;
    }

    public MarginResult calculate(Portfolio portfolio) {
        int scale = scaleFor(portfolio.currency());
        BigDecimal grossExposure = BigDecimal.ZERO;
        BigDecimal initialMargin = BigDecimal.ZERO;
        for (Position position : portfolio.positions()) {
            BigDecimal exposure = position.exposure();
            grossExposure = grossExposure.add(exposure);
            initialMargin = initialMargin.add(exposure.multiply(rateFor(position)));
        }

        BigDecimal maintenanceMargin = initialMargin.multiply(properties.maintenanceRatio());

        BigDecimal collateralValue = BigDecimal.ZERO;
        for (Collateral collateral : portfolio.collateral()) {
            collateralValue = collateralValue.add(
                    collateral.marketValue().multiply(BigDecimal.ONE.subtract(haircutFor(collateral))));
        }

        initialMargin = round(initialMargin, scale);
        maintenanceMargin = round(maintenanceMargin, scale);
        collateralValue = round(collateralValue, scale);

        boolean callRequired = collateralValue.compareTo(maintenanceMargin) < 0;
        BigDecimal callAmount = callRequired ? initialMargin.subtract(collateralValue) : round(BigDecimal.ZERO, scale);

        return new MarginResult(
                portfolio.accountId(),
                portfolio.currency(),
                round(grossExposure, scale),
                initialMargin,
                maintenanceMargin,
                collateralValue,
                collateralValue.subtract(initialMargin),
                callRequired,
                callAmount);
    }

    private BigDecimal rateFor(Position position) {
        BigDecimal rate = properties.initialMarginRates().get(position.assetClass());
        if (rate == null) {
            throw new IllegalStateException("No initial margin rate configured for " + position.assetClass());
        }
        return rate;
    }

    private BigDecimal haircutFor(Collateral collateral) {
        BigDecimal haircut = properties.haircuts().get(collateral.type());
        if (haircut == null) {
            throw new IllegalStateException("No haircut configured for " + collateral.type());
        }
        return haircut;
    }

    private static int scaleFor(Currency currency) {
        int digits = currency.getDefaultFractionDigits();
        return digits < 0 ? DEFAULT_SCALE : digits;
    }

    private static BigDecimal round(BigDecimal value, int scale) {
        return value.setScale(scale, ROUNDING);
    }
}
