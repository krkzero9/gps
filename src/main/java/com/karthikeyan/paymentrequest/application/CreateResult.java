package com.karthikeyan.paymentrequest.application;

import com.karthikeyan.paymentrequest.persistence.PaymentRequestEntity;

/** The outcome of a create: the payment request, and whether it is a replay of an earlier request with the same key. */
public record CreateResult(PaymentRequestEntity paymentRequest, boolean replayed) {
}