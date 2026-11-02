package com.example.margin.messaging;

import com.example.margin.domain.MarginCallEvent;

/** Port for publishing margin-call events; decouples the service from Kafka for unit testing. */
public interface MarginCallPublisher {

    void publish(MarginCallEvent event);
}
