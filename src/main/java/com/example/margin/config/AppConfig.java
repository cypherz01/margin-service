package com.example.margin.config;

import java.time.Clock;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class AppConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** Creates the margin-call topic on startup if it does not already exist. */
    @Bean
    NewTopic marginCallTopic(MarginProperties properties) {
        return TopicBuilder.name(properties.marginCallTopic()).partitions(3).replicas(1).build();
    }
}
