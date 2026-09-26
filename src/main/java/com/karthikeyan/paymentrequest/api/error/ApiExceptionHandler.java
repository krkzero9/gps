package com.karthikeyan.paymentrequest.api.error;

import com.karthikeyan.paymentrequest.api.PaymentRequestController;
import com.karthikeyan.paymentrequest.application.exception.ErrorCode;
import com.karthikeyan.paymentrequest.application.exception.PaymentRequestException;
import com.karthikeyan.paymentrequest.application.exception.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.karthikeyan.paymentrequest.application.exception.FieldViolation;
import java.util.List;

/**
 * The single place where errors become HTTP responses (RFC 9457 Problem Details + our "code").
 * Extends Spring's base handler so standard MVC errors (405, 415, ...) keep their correct status.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(PaymentRequestException.class)
    ProblemDetail handlePaymentRequestException(PaymentRequestException ex) {
        ProblemDetail problem = problem(ex.getCode(), ex.getMessage());
        if (ex instanceof ValidationException validation) {
            problem.setProperty("errors", validation.getViolations());
        }
        return problem;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return problem(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred.");
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
                                                                  HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        // Valid JSON, but a field has the wrong type, e.g. "amount": "abc" -> report it like a validation error.
        if (ex.getCause() instanceof MismatchedInputException mismatch && !mismatch.getPath().isEmpty()) {
            String field = mismatch.getPath().get(mismatch.getPath().size() - 1).getFieldName();
            if (field != null) {
                ProblemDetail problem = handlePaymentRequestException(new ValidationException(List.of(
                        new FieldViolation(field, "INVALID_TYPE", field + " has an invalid type or format."))));
                return ResponseEntity.status(problem.getStatus()).body(problem);
            }
        }
        return response(ErrorCode.MALFORMED_REQUEST, "The request body is missing or is not valid JSON.");
    }

    @Override
    protected ResponseEntity<Object> handleServletRequestBindingException(ServletRequestBindingException ex,
                                                                          HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (ex instanceof MissingRequestHeaderException missing
                && PaymentRequestController.IDEMPOTENCY_KEY_HEADER.equalsIgnoreCase(missing.getHeaderName())) {
            return response(ErrorCode.IDEMPOTENCY_KEY_MISSING, "The Idempotency-Key header is required.");
        }
        return super.handleServletRequestBindingException(ex, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex,
                                                        HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return response(ErrorCode.MALFORMED_REQUEST, "Invalid value for '" + ex.getPropertyName() + "'.");
    }

    /** The one mapping from business error codes to HTTP statuses. A new code must be added here to compile. */
    private static HttpStatus statusFor(ErrorCode code) {
        return switch (code) {
            case VALIDATION_FAILED, MALFORMED_REQUEST, IDEMPOTENCY_KEY_MISSING, IDEMPOTENCY_KEY_INVALID ->
                    HttpStatus.BAD_REQUEST;
            case PAYMENT_REQUEST_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    private static ProblemDetail problem(ErrorCode code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(statusFor(code), detail);
        problem.setTitle(code.getTitle());
        problem.setProperty("code", code.name());
        return problem;
    }

    private static ResponseEntity<Object> response(ErrorCode code, String detail) {
        ProblemDetail problem = problem(code, detail);
        return ResponseEntity.status(problem.getStatus()).body(problem);
    }
}