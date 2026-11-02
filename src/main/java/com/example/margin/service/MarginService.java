package com.example.margin.service;

import com.example.margin.calculation.MarginCalculator;
import com.example.margin.domain.MarginCallEvent;
import com.example.margin.domain.MarginResult;
import com.example.margin.domain.Portfolio;
import com.example.margin.messaging.MarginCallPublisher;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Calculates margin for a portfolio and raises a margin call when maintenance is breached. */
@Service
public class MarginService {

    private final MarginCalculator calculator;
    private final MarginCallPublisher publisher;
    private final Clock clock;

    public MarginService(MarginCalculator calculator, MarginCallPublisher publisher, Clock clock) {
        this.calculator = calculator;
        this.publisher = publisher;
        this.clock = clock;
    }

    public MarginResult evaluate(Portfolio portfolio) {
        MarginResult result = calculator.calculate(portfolio);
        if (result.marginCallRequired()) {
            publisher.publish(new MarginCallEvent(
                    UUID.randomUUID().toString(),
                    result.accountId(),
                    result.marginCallAmount(),
                    result.initialMargin(),
                    result.maintenanceMargin(),
                    result.collateralValue(),
                    clock.instant()));
        }
        return result;
    }
}
