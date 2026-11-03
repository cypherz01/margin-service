# Architecture

## Components

Ports and adapters ([ADR 0001](adr/0001-ports-and-adapters.md)): the margin model and domain have no framework,
transport or vendor dependencies, and `ArchitectureTest` enforces it.

```mermaid
flowchart LR
    client([Browser UI / API client])

    subgraph svc[margin-service]
        direction LR
        filter[CorrelationIdFilter]
        subgraph api[api]
            mc[MarginController]
            qc[QuoteController]
            eh[ApiExceptionHandler<br/>RFC 7807]
        end
        ms[service<br/>MarginService<br/>metrics + audit log]
        calc[calculation<br/>MarginCalculator<br/><i>pure</i>]
        dom[(domain<br/>Portfolio, Position,<br/>Collateral, MarginResult)]
        pub{{port<br/>MarginCallPublisher}}
        qp{{port<br/>QuoteProvider}}
        kafkaAd[messaging<br/>KafkaMarginCallPublisher]
        finn[marketdata<br/>FinnhubQuoteProvider]
    end

    kafka[(Kafka<br/>topic margin-calls)]
    finnhub[(Finnhub API)]
    prom[(Prometheus)]

    client -->|HTTP JSON| filter --> mc & qc
    mc --> ms --> calc --> dom
    ms --> pub -.implemented by.-> kafkaAd -->|acks=all| kafka
    qc --> qp -.implemented by.-> finn -->|HTTPS, key in header| finnhub
    prom -->|scrape /actuator/prometheus| svc
```

## Raising a margin call

The response is only `200` once the broker has acknowledged the event
([ADR 0002](adr/0002-acknowledge-margin-call-before-responding.md)).

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant API as MarginController
    participant S as MarginService
    participant M as MarginCalculator
    participant P as KafkaMarginCallPublisher
    participant K as Kafka

    C->>API: POST /api/v1/margin/calculations
    API->>API: validate bounds, currency (400 on failure)
    API->>S: evaluate(portfolio)
    S->>M: calculate(portfolio)
    M-->>S: MarginResult (collateral < maintenance)
    S->>P: publish(MarginCallEvent)
    P->>K: send(key = accountId, traceparent header)
    alt acknowledged within publish-timeout
        K-->>P: ack (all in-sync replicas)
        P-->>S: ok
        S->>S: count metric, write audit record
        S-->>API: result
        API-->>C: 200 + result, X-Correlation-Id
    else rejected, timed out or interrupted
        P-->>S: MarginCallPublishException
        S->>S: count failure, audit "NOT raised"
        S-->>API: exception
        API-->>C: 503 problem+json: no call raised, retry
    end
```

## Cross-cutting concerns

| Concern | Where |
|---|---|
| Input validation and limits | `MarginRequest` (Bean Validation), `Portfolio` invariants ([ADR 0005](adr/0005-bounded-inputs.md)) |
| Money arithmetic | `BigDecimal`, half-even, currency minor unit ([ADR 0003](adr/0003-decimal-arithmetic-and-rounding.md)) |
| Errors | `ApiExceptionHandler`: RFC 7807 problem details; internal causes logged, not returned |
| Correlation and tracing | `CorrelationIdFilter`, Micrometer Tracing; IDs on every log line and Kafka record |
| Audit | `audit.margin` logger, structured key-value pairs |
| Configuration | `MarginProperties`, `FinnhubProperties`: typed, validated at startup |
