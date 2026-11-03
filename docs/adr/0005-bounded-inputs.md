# 5. Bound every request field

**Status:** Accepted

## Context

`BigDecimal` accepts arbitrary exponents. The 9-character JSON value `1e5000000` parses instantly but took about
5 seconds of CPU to multiply and round in a local test, so a handful of concurrent requests could stall the
service. Unbounded lists and identifiers have similar, smaller costs, and identifiers end up in log lines.

## Decision

Validate before any arithmetic, with Bean Validation on the request DTO:

| Field | Limit |
|---|---|
| `quantity` | `@Digits(integer = 12, fraction = 6)` |
| `price` | `@PositiveOrZero`, `@Digits(integer = 12, fraction = 8)` |
| `marketValue` | `@PositiveOrZero`, `@Digits(integer = 15, fraction = 2)` |
| `positions` / `collateral` | at most 1,000 / 100 entries |
| `accountId` | 1–64 of `A-Z a-z 0-9 . _ : -` |
| `instrumentId` | 1–64 characters |

Margin parameters (`margin.*`) are validated at startup to lie in [0, 1].

## Consequences

- Worst-case request cost is bounded and small.
- `accountId` cannot inject newlines or markup into logs and audit records.
- Limits are generous for a demo service; a real deployment would set them from instrument and account reference
  data.
