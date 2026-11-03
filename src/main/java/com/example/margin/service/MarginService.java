package com.example.margin.service;

import com.example.margin.calculation.MarginCalculator;
import com.example.margin.domain.MarginCallEvent;
import com.example.margin.domain.MarginResult;
import com.example.margin.domain.Portfolio;
import com.example.margin.messaging.MarginCallPublishException;
import com.example.margin.messaging.MarginCallPublisher;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Calculates margin for a portfolio and raises a margin call when maintenance is breached.
 *
 * <p>Every evaluation is written to the {@code audit.margin} logger with its inputs' totals and outcome as
 * structured key-value pairs, so it can be routed to tamper-evident storage separately from operational logs.
 *
 * <p>Metrics (exported at {@code /actuator/prometheus}):
 * <ul>
 *   <li>{@code margin.calculations} – counter, tagged {@code outcome=margin_call|no_call} and {@code currency}</li>
 *   <li>{@code margin.call.amount} – distribution of call amounts, tagged {@code currency}</li>
 *   <li>{@code margin.call.publish.failures} – margin calls that could not be delivered</li>
 * </ul>
 */
@Service
public class MarginService {

    private static final Logger AUDIT = LoggerFactory.getLogger("audit.margin");

    private final MarginCalculator calculator;
    private final MarginCallPublisher publisher;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    public MarginService(MarginCalculator calculator, MarginCallPublisher publisher, Clock clock,
                         MeterRegistry meterRegistry) {
        this.calculator = calculator;
        this.publisher = publisher;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
    }

    public MarginResult evaluate(Portfolio portfolio) {
        MarginResult result = calculator.calculate(portfolio);
        String currency = result.currency().getCurrencyCode();

        if (!result.marginCallRequired()) {
            countCalculation("no_call", currency);
            audit(result, null);
            return result;
        }

        MarginCallEvent event = new MarginCallEvent(
                UUID.randomUUID().toString(),
                result.accountId(),
                result.currency(),
                result.marginCallAmount(),
                result.initialMargin(),
                result.maintenanceMargin(),
                result.collateralValue(),
                clock.instant());
        try {
            publisher.publish(event);
        } catch (MarginCallPublishException ex) {
            meterRegistry.counter("margin.call.publish.failures", "currency", currency).increment();
            AUDIT.atWarn()
                    .addKeyValue("accountId", result.accountId())
                    .addKeyValue("eventId", event.eventId())
                    .addKeyValue("marginCallAmount", result.marginCallAmount())
                    .addKeyValue("currency", currency)
                    .log("Margin call {} for account {} was required but NOT raised: publish failed",
                            event.eventId(), result.accountId());
            throw ex;
        }

        countCalculation("margin_call", currency);
        meterRegistry.summary("margin.call.amount", "currency", currency)
                .record(result.marginCallAmount().doubleValue());
        audit(result, event.eventId());
        return result;
    }

    private void countCalculation(String outcome, String currency) {
        meterRegistry.counter("margin.calculations", "outcome", outcome, "currency", currency).increment();
    }

    private static void audit(MarginResult result, String eventId) {
        AUDIT.atInfo()
                .addKeyValue("accountId", result.accountId())
                .addKeyValue("currency", result.currency().getCurrencyCode())
                .addKeyValue("initialMargin", result.initialMargin())
                .addKeyValue("maintenanceMargin", result.maintenanceMargin())
                .addKeyValue("collateralValue", result.collateralValue())
                .addKeyValue("marginCallRequired", result.marginCallRequired())
                .addKeyValue("marginCallAmount", result.marginCallAmount())
                .addKeyValue("eventId", eventId)
                .log("Margin evaluated for account {}: callRequired={}, callAmount={} {}, eventId={}",
                        result.accountId(), result.marginCallRequired(), result.marginCallAmount(),
                        result.currency().getCurrencyCode(), eventId);
    }
}
