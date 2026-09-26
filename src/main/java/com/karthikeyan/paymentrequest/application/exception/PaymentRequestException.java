package com.karthikeyan.paymentrequest.application.exception;

public class PaymentRequestException extends RuntimeException {

    private final ErrorCode code;

    public PaymentRequestException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode getCode() {
        return code;
    }
}