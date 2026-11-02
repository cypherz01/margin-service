package com.example.margin.config;

import com.example.margin.marketdata.FinnhubQuoteProvider;
import com.example.margin.marketdata.QuoteProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class MarketDataConfig {

    @Bean
    QuoteProvider quoteProvider(RestClient.Builder builder, FinnhubProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.timeout());
        requestFactory.setReadTimeout(properties.timeout());

        RestClient restClient = builder
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
        return new FinnhubQuoteProvider(restClient, properties.apiKey());
    }
}
