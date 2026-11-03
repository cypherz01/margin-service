package com.example.margin.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.margin.domain.MarginResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

/**
 * Full-stack test: HTTP request -> controller -> service -> calculator -> Kafka (embedded broker),
 * plus the operational endpoints (metrics, health probes) the service exposes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureObservability   // metrics export and tracing are off in tests unless explicitly enabled
@EmbeddedKafka(partitions = 1, bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class MarginApiKafkaIT {

    private static final String TOPIC = "margin-calls";
    private static final String URL = "/api/v1/margin/calculations";

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private EmbeddedKafkaBroker broker;

    @Autowired
    private ObjectMapper objectMapper;

    private Consumer<String, String> consumer;

    @BeforeEach
    void subscribe() {
        Map<String, Object> props = KafkaTestUtils.consumerProps("it-" + UUID.randomUUID(), "true", broker);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumer = new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), new StringDeserializer())
                .createConsumer();
        consumer.subscribe(List.of(TOPIC));
    }

    @AfterEach
    void close() {
        consumer.close();
    }

    private static Map<String, Object> request(String accountId, int cashCollateral) {
        return Map.of(
                "accountId", accountId,
                "currency", "USD",
                "positions", List.of(Map.of(
                        "instrumentId", "AAPL", "assetClass", "EQUITY", "quantity", 100, "price", 50)),
                "collateral", List.of(Map.of("type", "CASH", "marketValue", cashCollateral)));
    }

    @Test
    void breachOfMaintenanceMarginReturnsResultAndPublishesMarginCall() throws Exception {
        String accountId = "ACC-" + UUID.randomUUID();

        ResponseEntity<MarginResult> response = rest.postForEntity(URL, request(accountId, 500), MarginResult.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().currency().getCurrencyCode()).isEqualTo("USD");
        assertThat(response.getBody().initialMargin()).isEqualByComparingTo("750");
        assertThat(response.getBody().marginCallRequired()).isTrue();
        assertThat(response.getBody().marginCallAmount()).isEqualByComparingTo("250");

        ConsumerRecord<String, String> record = recordsFor(accountId, Duration.ofSeconds(15)).stream()
                .findFirst()
                .orElseThrow(() -> new AssertionError("No margin call published for " + accountId));

        JsonNode event = objectMapper.readTree(record.value());
        assertThat(record.key()).isEqualTo(accountId);
        assertThat(event.get("accountId").asText()).isEqualTo(accountId);
        assertThat(event.get("currency").asText()).isEqualTo("USD");
        assertThat(event.get("callAmount").decimalValue()).isEqualByComparingTo("250");
        assertThat(event.get("eventId").asText()).isNotBlank();
        assertThat(record.headers().lastHeader("traceparent"))
                .as("trace context propagated to consumers")
                .isNotNull();
    }

    @Test
    void echoesCorrelationIdSoCallersCanQuoteItInSupportRequests() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Correlation-Id", "caller-supplied-123");

        ResponseEntity<String> response = rest.exchange(URL, HttpMethod.POST,
                new HttpEntity<>(request("ACC-" + UUID.randomUUID(), 1000), headers), String.class);

        assertThat(response.getHeaders().getFirst("X-Correlation-Id")).isEqualTo("caller-supplied-123");
    }

    @Test
    void exposesMarginMetricsForPrometheus() {
        rest.postForEntity(URL, request("ACC-" + UUID.randomUUID(), 1000), MarginResult.class);

        ResponseEntity<String> scrape = rest.getForEntity("/actuator/prometheus", String.class);

        assertThat(scrape.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(scrape.getBody())
                .contains("margin_calculations_total{")
                .contains("http_server_requests_seconds_count{");
    }

    @Test
    void exposesKubernetesHealthProbes() {
        assertThat(rest.getForEntity("/actuator/health/liveness", String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(rest.getForEntity("/actuator/health/readiness", String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void healthyPortfolioPublishesNothing() {
        String accountId = "ACC-" + UUID.randomUUID();

        ResponseEntity<MarginResult> response = rest.postForEntity(URL, request(accountId, 1000), MarginResult.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().marginCallRequired()).isFalse();
        assertThat(recordsFor(accountId, Duration.ofSeconds(3))).isEmpty();
    }

    @Test
    void invalidRequestIsRejectedWithProblemDetail() {
        ResponseEntity<String> response = rest.postForEntity(URL, Map.of("positions", List.of()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType())
                .satisfies(type -> assertThat(type.isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)).isTrue());
    }

    /** Polls until a record keyed by {@code accountId} arrives or the timeout elapses. */
    private List<ConsumerRecord<String, String>> recordsFor(String accountId, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            List<ConsumerRecord<String, String>> matches = StreamSupport
                    .stream(consumer.poll(Duration.ofMillis(250)).records(TOPIC).spliterator(), false)
                    .filter(r -> accountId.equals(r.key()))
                    .toList();
            if (!matches.isEmpty()) {
                return matches;
            }
        }
        return List.of();
    }
}
