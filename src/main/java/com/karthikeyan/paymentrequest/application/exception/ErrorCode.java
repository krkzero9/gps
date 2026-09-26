package com.karthikeyan.paymentrequest.application.exception;

/**
 * Every error code the API can return. The codes describe business outcomes;
 * the HTTP status for each code is decided in the web layer (ApiExceptionHandler).
 */
public enum ErrorCode {

    VALIDATION_FAILED("Validation failed"),
    MALFORMED_REQUEST("Malformed request"),
    IDEMPOTENCY_KEY_MISSING("Missing Idempotency-Key"),
    IDEMPOTENCY_KEY_INVALID("Invalid Idempotency-Key"),
    PAYMENT_REQUEST_NOT_FOUND("Payment request not found"),
    INTERNAL_ERROR("Internal error");

    private final String title;

    ErrorCode(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }
}