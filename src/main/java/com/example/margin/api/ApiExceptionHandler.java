package com.example.margin.api;

import com.example.margin.marketdata.MarketDataUnavailableException;
import com.example.margin.marketdata.QuoteNotFoundException;
import com.example.margin.messaging.MarginCallPublishException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Returns RFC 7807 problem details. Validation and malformed-body errors are handled
 * by the {@link ResponseEntityExceptionHandler} base class as 400s.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    /** Domain invariants rejected at construction time (e.g. negative price). */
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /** Missing margin configuration for a requested asset/collateral class. */
    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail handleIllegalState(IllegalStateException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    /** A required margin call could not be delivered; the client should retry the calculation. */
    @ExceptionHandler(MarginCallPublishException.class)
    ProblemDetail handleMarginCallPublish(MarginCallPublishException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "Margin call could not be published; retry the request");
    }

    /** Ticker symbol the market-data provider doesn't recognise. */
    @ExceptionHandler(QuoteNotFoundException.class)
    ProblemDetail handleQuoteNotFound(QuoteNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /** Provider down, rate-limited or not configured; the reason is logged, not returned to clients. */
    @ExceptionHandler(MarketDataUnavailableException.class)
    ProblemDetail handleMarketDataUnavailable(MarketDataUnavailableException ex) {
        logger.warn(ex.getMessage(), ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "Live prices are temporarily unavailable; enter the price manually");
    }
}
