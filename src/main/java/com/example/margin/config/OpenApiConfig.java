package com.example.margin.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Top-level metadata for the generated OpenAPI document (served at /v3/api-docs). */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI marginServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Margin Service API")
                        .version("v1")
                        .description("""
                                Calculates initial and maintenance margin and haircut collateral value for a \
                                portfolio, and raises a margin call (published to the Kafka topic `margin-calls`, \
                                see docs/asyncapi.yaml) when collateral falls below maintenance margin.

                                Errors use RFC 7807 `application/problem+json`. Every response carries an \
                                `X-Correlation-Id` header to quote in support requests.

                                The margin model is illustrative only, not SPAN, SIMM or a regulatory methodology."""))
                .tags(List.of(
                        new Tag().name("Margin").description("Margin and collateral calculations"),
                        new Tag().name("Market data").description("Live share prices")));
    }
}
