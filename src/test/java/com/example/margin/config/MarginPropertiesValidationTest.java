package com.example.margin.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.margin.domain.AssetClass;
import com.example.margin.domain.CollateralType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** A mistyped margin parameter must stop the service starting rather than silently mis-price risk. */
class MarginPropertiesValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    private static MarginProperties properties(String equityRate, String maintenanceRatio, String cashHaircut) {
        return new MarginProperties(
                Map.of(AssetClass.EQUITY, new BigDecimal(equityRate)),
                new BigDecimal(maintenanceRatio),
                Map.of(CollateralType.CASH, new BigDecimal(cashHaircut)),
                "margin-calls",
                Duration.ofSeconds(5));
    }

    @Test
    void acceptsFractionsBetweenZeroAndOne() {
        assertThat(validator.validate(properties("0.15", "0.75", "0"))).isEmpty();
        assertThat(validator.validate(properties("1", "1", "1"))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.01", "1.01", "15"})
    void rejectsInitialMarginRateOutsideZeroToOne(String rate) {
        assertThat(paths(validator.validate(properties(rate, "0.75", "0"))))
                .anyMatch(path -> path.startsWith("initialMarginRates"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.01", "1.01", "25"})
    void rejectsHaircutOutsideZeroToOne(String haircut) {
        assertThat(paths(validator.validate(properties("0.15", "0.75", haircut))))
                .anyMatch(path -> path.startsWith("haircuts"));
    }

    private static Set<String> paths(Set<ConstraintViolation<MarginProperties>> violations) {
        return violations.stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }
}
