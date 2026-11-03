# 3. `BigDecimal` arithmetic, rounded half-even once, to the currency's minor unit

**Status:** Accepted

## Context

Binary floating point cannot represent most decimal amounts exactly (`0.1 + 0.2 != 0.3`), and per-line rounding
accumulates error across large portfolios. Different currencies have different minor units: USD has 2 decimals,
JPY has 0, KWD has 3.

## Decision

- All amounts are `BigDecimal`, end to end: JSON in, model, JSON and Kafka out.
- Sums and products keep full precision; each reported figure is rounded **once**, at the end.
- Rounding is `HALF_EVEN` (banker's rounding), which does not bias totals upwards over many operations.
- The scale is the portfolio currency's ISO 4217 minor unit (`Currency.getDefaultFractionDigits()`), falling back
  to 2 for currencies with none (e.g. XAU).

## Consequences

- Results are reproducible to the minor unit and match what a reconciliation system would compute.
- Unbounded `BigDecimal` input is a denial-of-service risk; see [0005](0005-bounded-inputs.md).
- Consumers must parse amounts as decimals, not doubles, to keep exactness.
