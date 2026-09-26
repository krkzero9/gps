package com.karthikeyan.paymentrequest.application.exception;

import java.util.List;

/** The only error that carries extra data: every invalid field, reported at once. */
public class ValidationException extends PaymentRequestException {

    private final List<FieldViolation> violations;

    public ValidationException(List<FieldViolation> violations) {
        super(ErrorCode.VALIDATION_FAILED, "The request contains " + violations.size() + " invalid field(s).");
        this.violations = List.copyOf(violations);
    }

    public List<FieldViolation> getViolations() {
        return violations;
    }
}