package com.example.margin.domain;

import java.math.BigDecimal;

/**
 * Outcome of a margin calculation.
 *
 * @param grossExposure      sum of absolute position values
 * @param initialMargin      margin required to open / restore the portfolio
 * @param maintenanceMargin  minimum margin before a call is triggered
 * @param collateralValue    collateral value after haircuts
 * @param excess             collateralValue - initialMargin (negative = shortfall)
 * @param marginCallRequired true when collateralValue is below maintenanceMargin
 * @param marginCallAmount   amount needed to restore collateral to initial margin (0 if no call)
 */
public record MarginResult(
        String accountId,
        BigDecimal grossExposure,
        BigDecimal initialMargin,
        BigDecimal maintenanceMargin,
        BigDecimal collateralValue,
        BigDecimal excess,
        boolean marginCallRequired,
        BigDecimal marginCallAmount) {
}
