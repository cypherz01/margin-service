package com.example.margin.domain;

/** Type of collateral posted; drives the haircut applied to its market value. */
public enum CollateralType {
    CASH,
    GOVERNMENT_BOND,
    CORPORATE_BOND,
    EQUITY
}
