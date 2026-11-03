# 4. Reject mixed-currency portfolios rather than convert

**Status:** Accepted

## Context

The first version had no currency at all, so a USD equity and a JPY cash balance would have been added together.
Supporting multiple currencies properly needs FX rates from an agreed source and time (e.g. end-of-day fixing),
recorded alongside each result for audit.

## Decision

- A portfolio has a required ISO 4217 reporting `currency`.
- Each position and collateral item may state its own currency and otherwise inherits the portfolio's.
- `Portfolio` rejects any line in a different currency (`IllegalArgumentException` → `400`), so the invariant holds
  however a portfolio is built, not only through the REST API.

## Consequences

- No silent mixing of units; the error message names the offending line.
- Clients holding multi-currency books must convert before calling, for now.
- Adding FX conversion later is additive: a rate source port, conversion in the calculator, and the rates used
  recorded on the result and the margin-call event.
