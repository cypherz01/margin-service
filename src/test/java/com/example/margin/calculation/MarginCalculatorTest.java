package com.example.margin.calculation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.margin.config.MarginProperties;
import com.example.margin.domain.AssetClass;
import com.example.margin.domain.Collateral;
import com.example.margin.domain.CollateralType;
import com.example.margin.domain.MarginResult;
import com.example.margin.domain.Portfolio;
import com.example.margin.domain.Position;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MarginCalculatorTest {

    private static final MarginProperties PROPERTIES = new MarginProperties(
            Map.of(
                    AssetClass.EQUITY, new BigDecimal("0.15"),
                    AssetClass.BOND, new BigDecimal("0.05"),
                    AssetClass.FX, new BigDecimal("0.04"),
                    AssetClass.COMMODITY, new BigDecimal("0.10")),
            new BigDecimal("0.75"),
            Map.of(
                    CollateralType.CASH, BigDecimal.ZERO,
                    CollateralType.GOVERNMENT_BOND, new BigDecimal("0.02"),
                    CollateralType.CORPORATE_BOND, new BigDecimal("0.10"),
                    CollateralType.EQUITY, new BigDecimal("0.25")),
            "margin-calls",
            Duration.ofSeconds(5));

    private final MarginCalculator calculator = new MarginCalculator(PROPERTIES);

    private static Position position(AssetClass assetClass, String qty, String price) {
        return new Position("INST-" + assetClass, assetClass, new BigDecimal(qty), new BigDecimal(price));
    }

    private static Collateral cash(String amount) {
        return new Collateral(CollateralType.CASH, new BigDecimal(amount));
    }

    private static Portfolio portfolio(List<Position> positions, List<Collateral> collateral) {
        return new Portfolio("ACC-1", positions, collateral);
    }

    @Nested
    @DisplayName("initial and maintenance margin")
    class Requirements {

        @Test
        void appliesAssetClassRateToExposure() {
            MarginResult result = calculator.calculate(
                    portfolio(List.of(position(AssetClass.EQUITY, "100", "50")), List.of()));

            assertThat(result.grossExposure()).isEqualByComparingTo("5000");
            assertThat(result.initialMargin()).isEqualByComparingTo("750");
            assertThat(result.maintenanceMargin()).isEqualByComparingTo("562.50");
        }

        @Test
        void treatsShortPositionsByAbsoluteExposure() {
            MarginResult result = calculator.calculate(
                    portfolio(List.of(position(AssetClass.EQUITY, "-100", "50")), List.of()));

            assertThat(result.initialMargin()).isEqualByComparingTo("750");
        }

        @Test
        void sumsMarginAcrossAssetClasses() {
            MarginResult result = calculator.calculate(portfolio(
                    List.of(position(AssetClass.EQUITY, "100", "50"),   // 5,000 * 15% = 750
                            position(AssetClass.BOND, "200", "100"),    // 20,000 * 5% = 1,000
                            position(AssetClass.FX, "10000", "1.25")),  // 12,500 * 4% = 500
                    List.of()));

            assertThat(result.grossExposure()).isEqualByComparingTo("37500");
            assertThat(result.initialMargin()).isEqualByComparingTo("2250");
        }

        @Test
        void emptyPortfolioRequiresNoMargin() {
            MarginResult result = calculator.calculate(portfolio(List.of(), List.of()));

            assertThat(result.initialMargin()).isEqualByComparingTo("0");
            assertThat(result.marginCallRequired()).isFalse();
        }

        @Test
        void failsFastWhenAssetClassHasNoConfiguredRate() {
            MarginCalculator incomplete = new MarginCalculator(new MarginProperties(
                    Map.of(AssetClass.EQUITY, new BigDecimal("0.15")),
                    new BigDecimal("0.75"),
                    Map.of(CollateralType.CASH, BigDecimal.ZERO),
                    "margin-calls",
                    Duration.ofSeconds(5)));

            assertThatThrownBy(() -> incomplete.calculate(
                    portfolio(List.of(position(AssetClass.COMMODITY, "1", "1")), List.of())))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("COMMODITY");
        }
    }

    @Nested
    @DisplayName("collateral valuation")
    class CollateralValuation {

        @Test
        void appliesHaircutPerCollateralType() {
            MarginResult result = calculator.calculate(portfolio(
                    List.of(),
                    List.of(cash("1000"),                                                         // 1,000
                            new Collateral(CollateralType.GOVERNMENT_BOND, new BigDecimal("1000")), // 980
                            new Collateral(CollateralType.EQUITY, new BigDecimal("1000")))));      // 750

            assertThat(result.collateralValue()).isEqualByComparingTo("2730");
        }
    }

    @Nested
    @DisplayName("margin calls")
    class MarginCalls {

        // Portfolio: 100 EQUITY @ 50 -> IM 750, MM 562.50
        @ParameterizedTest(name = "collateral {0} -> call={1}, amount={2}, excess={3}")
        @CsvSource({
                "1000,   false, 0,      250",
                "750,    false, 0,      0",
                "600,    false, 0,      -150",   // below IM but above MM: no call yet
                "562.50, false, 0,      -187.50", // exactly at MM: no call
                "500,    true,  250,    -250",    // breach: call restores to IM
                "0,      true,  750,    -750"
        })
        void issuesCallOnlyWhenCollateralFallsBelowMaintenance(
                String collateral, boolean callRequired, String callAmount, String excess) {
            MarginResult result = calculator.calculate(portfolio(
                    List.of(position(AssetClass.EQUITY, "100", "50")), List.of(cash(collateral))));

            assertThat(result.marginCallRequired()).isEqualTo(callRequired);
            assertThat(result.marginCallAmount()).isEqualByComparingTo(callAmount);
            assertThat(result.excess()).isEqualByComparingTo(excess);
        }
    }
}
