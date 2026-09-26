package com.karthikeyan.paymentrequest.application;

/** The input of "settle a payment request": the reference of the payment that paid it (e.g. a bank transaction id). */
public record SettleCommand(String settlementReference) {
}