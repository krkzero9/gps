package com.karthikeyan.paymentrequest.application;

import java.math.BigDecimal;

public record CreatePaymentRequestCommand(
        String recipientName,
        String recipientIban,
        BigDecimal amount,
        String currency,
        String purpose) {
}