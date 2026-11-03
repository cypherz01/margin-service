# Architecture Decision Records

Short records of significant design decisions, in the [Nygard format](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions):
context, decision, consequences. Records are immutable once accepted; a change of mind is a new record that
supersedes the old one.

| # | Decision | Status |
|---|---|---|
| [0001](0001-ports-and-adapters.md) | Ports and adapters around a pure margin model | Accepted |
| [0002](0002-acknowledge-margin-call-before-responding.md) | Report a margin call only after the broker acknowledges it | Accepted |
| [0003](0003-decimal-arithmetic-and-rounding.md) | `BigDecimal` arithmetic, rounded half-even once, to the currency's minor unit | Accepted |
| [0004](0004-single-currency-portfolios.md) | Reject mixed-currency portfolios rather than convert | Accepted |
| [0005](0005-bounded-inputs.md) | Bound every request field | Accepted |
| [0006](0006-transactional-outbox-and-idempotency.md) | Transactional outbox and idempotency keys for margin calls | Proposed |
