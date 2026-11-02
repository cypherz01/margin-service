package com.example.margin.messaging;

/** A margin call could not be confirmed as published, so the caller must not treat it as raised. */
public class MarginCallPublishException extends RuntimeException {

    public MarginCallPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
