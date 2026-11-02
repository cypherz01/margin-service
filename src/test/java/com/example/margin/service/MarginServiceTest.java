package com.example.margin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.margin.calculation.MarginCalculator;
import com.example.margin.domain.MarginCallEvent;
import com.example.margin.domain.MarginResult;
import com.example.margin.domain.Portfolio;
import com.example.margin.messaging.MarginCallPublishException;
import com.example.margin.messaging.MarginCallPublisher;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarginServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-02T12:00:00Z");

    @Mock
    private MarginCalculator calculator;

    @Mock
    private MarginCallPublisher publisher;

    @Captor
    private ArgumentCaptor<MarginCallEvent> eventCaptor;

    private MarginService service;

    private final Portfolio portfolio = new Portfolio("ACC-1", List.of(), List.of());

    @BeforeEach
    void setUp() {
        service = new MarginService(calculator, publisher, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static MarginResult result(boolean callRequired, String callAmount) {
        return new MarginResult("ACC-1",
                new BigDecimal("5000.00"), new BigDecimal("750.00"), new BigDecimal("562.50"),
                new BigDecimal("500.00"), new BigDecimal("-250.00"),
                callRequired, new BigDecimal(callAmount));
    }

    @Test
    void publishesMarginCallWhenCalculatorReportsBreach() {
        when(calculator.calculate(portfolio)).thenReturn(result(true, "250.00"));

        MarginResult returned = service.evaluate(portfolio);

        assertThat(returned.marginCallRequired()).isTrue();
        verify(publisher).publish(eventCaptor.capture());
        MarginCallEvent event = eventCaptor.getValue();
        assertThat(event.accountId()).isEqualTo("ACC-1");
        assertThat(event.callAmount()).isEqualByComparingTo("250.00");
        assertThat(event.initialMargin()).isEqualByComparingTo("750.00");
        assertThat(event.issuedAt()).isEqualTo(NOW);
        assertThat(event.eventId()).isNotBlank();
    }

    @Test
    void doesNotPublishWhenNoCallRequired() {
        when(calculator.calculate(portfolio)).thenReturn(result(false, "0.00"));

        service.evaluate(portfolio);

        verify(publisher, never()).publish(any());
    }

    @Test
    void propagatesPublishFailureSoCallerIsNotToldTheCallWasRaised() {
        when(calculator.calculate(portfolio)).thenReturn(result(true, "250.00"));
        MarginCallPublishException failure = new MarginCallPublishException("broker unavailable", null);
        doThrow(failure).when(publisher).publish(any());

        assertThatThrownBy(() -> service.evaluate(portfolio)).isSameAs(failure);
    }
}
