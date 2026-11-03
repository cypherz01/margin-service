# 6. Transactional outbox and idempotency keys for margin calls

**Status:** Proposed

## Context

[0002](0002-acknowledge-margin-call-before-responding.md) guarantees a call is never reported as raised when it
was not. It does not guarantee the converse: if the broker acknowledges after the publish timeout, the client
receives `503`, retries, and a second margin call with a new `eventId` is published. The service also keeps no
record of calculations or calls, which an auditor would expect.

## Proposed decision

1. Persist each calculation and any margin call in Postgres (schema managed by Flyway).
2. Write the margin-call event to an `outbox` table **in the same transaction**; a relay publishes outbox rows
   to Kafka and marks them sent. The database becomes the single source of truth, and the API can return `200`
   as soon as the transaction commits.
3. Accept an `Idempotency-Key` request header. A repeated key with the same body returns the stored result and
   never raises a second call; a repeated key with a different body is rejected with `422`.
4. Consumers de-duplicate on `eventId`, which is now stable across relay retries.

## Consequences

- Exactly one margin call per logical request, and a durable audit trail of every calculation.
- Request latency no longer depends on the broker; a broker outage delays delivery instead of failing requests.
- Adds a database, a relay process (polling or CDC, e.g. Debezium) and Testcontainers-based integration tests.
