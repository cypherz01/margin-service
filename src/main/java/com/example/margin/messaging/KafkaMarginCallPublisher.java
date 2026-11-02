package com.example.margin.messaging;

import com.example.margin.config.MarginProperties;
import com.example.margin.domain.MarginCallEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/** Publishes margin calls to Kafka, keyed by account ID so events per account stay ordered. */
@Component
public class KafkaMarginCallPublisher implements MarginCallPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaMarginCallPublisher.class);

    private final KafkaTemplate<String, MarginCallEvent> kafkaTemplate;
    private final String topic;

    public KafkaMarginCallPublisher(KafkaTemplate<String, MarginCallEvent> kafkaTemplate,
                                    MarginProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = properties.marginCallTopic();
    }

    @Override
    public void publish(MarginCallEvent event) {
        kafkaTemplate.send(topic, event.accountId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish margin call {} for account {}",
                                event.eventId(), event.accountId(), ex);
                    } else {
                        log.info("Published margin call {} for account {} (amount {})",
                                event.eventId(), event.accountId(), event.callAmount());
                    }
                });
    }
}
