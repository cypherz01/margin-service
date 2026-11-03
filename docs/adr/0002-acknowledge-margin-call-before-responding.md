# 2. Report a margin call only after the broker acknowledges it

**Status:** Accepted

## Context

A margin call that the API reports as raised but that never reaches downstream systems (collateral management,
client notification) is worse than an error: the client believes the call is in flight, and nobody acts on it.

## Decision

- The producer uses `acks=all` and `enable.idempotence=true`.
- `KafkaMarginCallPublisher` blocks until the broker acknowledges the record, up to `margin.publish-timeout`
  (default 5 s).
- Any failure (broker rejection, timeout, interrupt, synchronous send error) raises `MarginCallPublishException`,
  which the API maps to `503 Service Unavailable` with "no call has been raised, retry".
- Calculations that do not require a call never touch Kafka, so they keep working during a broker outage.

## Consequences

- The HTTP response is an honest statement of what happened: 200 means the call is durably on the topic.
- Request latency includes the broker round trip, and a request thread is held for up to the timeout.
- If the broker acknowledges *after* the timeout, the event was published but the client was told to retry,
  so a retry can publish a duplicate. Accepted for now; addressed by [0006](0006-transactional-outbox-and-idempotency.md).
