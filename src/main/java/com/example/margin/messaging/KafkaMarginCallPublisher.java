package com.example.margin.messaging;

import com.example.margin.config.MarginProperties;
import com.example.margin.domain.MarginCallEvent;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
    private final Duration publishTimeout;

    public KafkaMarginCallPublisher(KafkaTemplate<String, MarginCallEvent> kafkaTemplate,
                                    MarginProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = properties.marginCallTopic();
        this.publishTimeout = properties.publishTimeout();
    }

    /**
     * Blocks until the broker acknowledges the event, so a call is never reported as raised
     * when it was not delivered.
     *
     * @throws MarginCallPublishException if the send fails, is not acknowledged in time, or is interrupted
     */
    @Override
    public void publish(MarginCallEvent event) {
        try {
            kafkaTemplate.send(topic, event.accountId(), event)
                    .get(publishTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw failure(event, ex);
        } catch (ExecutionException ex) {
            throw failure(event, ex.getCause());
        } catch (TimeoutException | RuntimeException ex) {
            throw failure(event, ex);
        }
        log.info("Published margin call {} for account {} (amount {})",
                event.eventId(), event.accountId(), event.callAmount());
    }

    private static MarginCallPublishException failure(MarginCallEvent event, Throwable cause) {
        log.error("Failed to publish margin call {} for account {}", event.eventId(), event.accountId(), cause);
        return new MarginCallPublishException(
                "Failed to publish margin call " + event.eventId() + " for account " + event.accountId(), cause);
    }
}
