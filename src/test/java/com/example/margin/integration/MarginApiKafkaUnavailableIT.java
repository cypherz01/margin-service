package com.example.margin.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.margin.domain.MarginResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Full-stack test with no reachable Kafka broker: a margin call that cannot be published
 * must fail the request rather than be silently dropped.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.kafka.bootstrap-servers=localhost:1",           // nothing listens here
        "spring.kafka.admin.auto-create=false",                 // don't block startup creating topics
        "spring.kafka.producer.properties.max.block.ms=1000",
        "margin.publish-timeout=3s"
})
class MarginApiKafkaUnavailableIT {

    private static final String URL = "/api/v1/margin/calculations";

    @Autowired
    private TestRestTemplate rest;

    private static Map<String, Object> request(int cashCollateral) {
        return Map.of(
                "accountId", "ACC-1",
                "currency", "USD",
                "positions", List.of(Map.of(
                        "instrumentId", "AAPL", "assetClass", "EQUITY", "quantity", 100, "price", 50)),
                "collateral", List.of(Map.of("type", "CASH", "marketValue", cashCollateral)));
    }

    @Test
    void breachIsReportedAsServiceUnavailableWhenMarginCallCannotBePublished() {
        ResponseEntity<String> response = rest.postForEntity(URL, request(500), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getHeaders().getContentType())
                .satisfies(type -> assertThat(type.isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)).isTrue());
    }

    @Test
    void healthyPortfolioIsUnaffectedByKafkaOutage() {
        ResponseEntity<MarginResult> response = rest.postForEntity(URL, request(1000), MarginResult.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().marginCallRequired()).isFalse();
    }
}
