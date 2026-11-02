package com.example.margin.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.margin.config.MarginProperties;
import com.example.margin.domain.MarginCallEvent;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

/**
 * The publisher must not report success until the broker has acknowledged the event,
 * and must surface every failure so the caller never believes a lost margin call was raised.
 */
@ExtendWith(MockitoExtension.class)
class KafkaMarginCallPublisherTest {

    private static final String TOPIC = "margin-calls";

    private static final MarginCallEvent EVENT = new MarginCallEvent(
            "evt-1", "ACC-1",
            new BigDecimal("250.00"), new BigDecimal("750.00"), new BigDecimal("562.50"),
            new BigDecimal("500.00"), Instant.parse("2026-11-02T12:00:00Z"));

    @Mock
    private KafkaTemplate<String, MarginCallEvent> kafkaTemplate;

    @AfterEach
    void clearInterruptFlag() {
        Thread.interrupted();
    }

    private KafkaMarginCallPublisher publisher(Duration publishTimeout) {
        return new KafkaMarginCallPublisher(kafkaTemplate,
                new MarginProperties(Map.of(), new BigDecimal("0.75"), Map.of(), TOPIC, publishTimeout));
    }

    private static SendResult<String, MarginCallEvent> acknowledged() {
        return new SendResult<>(
                new ProducerRecord<>(TOPIC, EVENT.accountId(), EVENT),
                new RecordMetadata(new TopicPartition(TOPIC, 0), 0, 0, 0, 0, 0));
    }

    @Test
    void sendsToConfiguredTopicKeyedByAccountId() {
        when(kafkaTemplate.send(TOPIC, "ACC-1", EVENT)).thenReturn(CompletableFuture.completedFuture(acknowledged()));

        assertThatCode(() -> publisher(Duration.ofSeconds(2)).publish(EVENT)).doesNotThrowAnyException();

        verify(kafkaTemplate).send(TOPIC, "ACC-1", EVENT);
    }

    @Test
    void waitsForBrokerAcknowledgementBeforeReturning() {
        CompletableFuture<SendResult<String, MarginCallEvent>> pending = new CompletableFuture<>();
        when(kafkaTemplate.send(TOPIC, "ACC-1", EVENT)).thenReturn(pending);
        CompletableFuture.delayedExecutor(200, TimeUnit.MILLISECONDS).execute(() -> pending.complete(acknowledged()));

        publisher(Duration.ofSeconds(2)).publish(EVENT);

        assertThat(pending).isCompleted();
    }

    @Test
    void failsWhenBrokerRejectsTheEvent() {
        when(kafkaTemplate.send(TOPIC, "ACC-1", EVENT))
                .thenReturn(CompletableFuture.failedFuture(new KafkaException("broker unavailable")));

        assertThatThrownBy(() -> publisher(Duration.ofSeconds(2)).publish(EVENT))
                .isInstanceOf(MarginCallPublishException.class)
                .hasMessageContaining("evt-1")
                .hasMessageContaining("ACC-1")
                .hasRootCauseInstanceOf(KafkaException.class);
    }

    @Test
    void failsWhenAcknowledgementDoesNotArriveWithinTimeout() {
        when(kafkaTemplate.send(TOPIC, "ACC-1", EVENT)).thenReturn(new CompletableFuture<>());
        KafkaMarginCallPublisher publisher = publisher(Duration.ofMillis(100));

        assertTimeoutPreemptively(Duration.ofSeconds(2), () ->
                assertThatThrownBy(() -> publisher.publish(EVENT))
                        .isInstanceOf(MarginCallPublishException.class)
                        .hasMessageContaining("evt-1")
                        .hasRootCauseInstanceOf(TimeoutException.class));
    }

    /** e.g. serialization errors, or metadata unavailable after max.block.ms. */
    @Test
    void failsWhenSendThrowsSynchronously() {
        when(kafkaTemplate.send(TOPIC, "ACC-1", EVENT)).thenThrow(new KafkaException("metadata not available"));

        assertThatThrownBy(() -> publisher(Duration.ofSeconds(2)).publish(EVENT))
                .isInstanceOf(MarginCallPublishException.class)
                .hasMessageContaining("evt-1")
                .hasRootCauseInstanceOf(KafkaException.class);
    }

    @Test
    void preservesInterruptStatusWhenInterruptedWhileWaiting() {
        when(kafkaTemplate.send(TOPIC, "ACC-1", EVENT)).thenReturn(new CompletableFuture<>());
        Thread.currentThread().interrupt();

        assertThatThrownBy(() -> publisher(Duration.ofSeconds(2)).publish(EVENT))
                .isInstanceOf(MarginCallPublishException.class)
                .hasRootCauseInstanceOf(InterruptedException.class);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }
}
