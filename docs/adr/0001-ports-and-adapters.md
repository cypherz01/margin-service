# 1. Ports and adapters around a pure margin model

**Status:** Accepted

## Context

The margin model is the part of the service most likely to be reviewed by risk and audit, and the part that most
needs exhaustive testing. Kafka and the Finnhub market-data API are delivery details that are likely to change
(another broker, a licensed price feed).

## Decision

- `calculation.MarginCalculator` is a pure function of a `Portfolio` and `MarginProperties`: no I/O, no clock, no
  framework types beyond the `@Component` stereotype.
- The `domain` package is plain Java records and enums.
- Outbound integrations sit behind ports owned by the core: `MarginCallPublisher` (Kafka adapter
  `KafkaMarginCallPublisher`) and `QuoteProvider` (adapter `FinnhubQuoteProvider`).
- These rules are enforced by `ArchitectureTest` (ArchUnit), so they fail the build rather than erode silently.

## Consequences

- The model is tested with plain JUnit, parameterised over thresholds, in milliseconds.
- The service is unit-tested with a mocked publisher and a fixed `Clock`.
- Swapping Kafka or the price vendor touches one adapter and one configuration class.
- Slightly more types than a single-layer design.
