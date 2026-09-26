package com.karthikeyan.paymentrequest.application.exception;

public record FieldViolation(String field, String code, String message) {
}