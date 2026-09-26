package com.karthikeyan.paymentrequest.api.dto;

import com.karthikeyan.paymentrequest.persistence.PaymentRequestEntity;

import java.time.Instant;
import java.util.UUID;

public record PaymentRequestResponse(
        UUID id,
        String status,
        String recipientName,
        String recipientIban,
        String amount,
        String currency,
        String purpose,
        Instant createdAt,
        Instant settledAt,
        String settlementReference) {

    public static PaymentRequestResponse from(PaymentRequestEntity entity) {
        return new PaymentRequestResponse(
                entity.getId(),
                entity.getStatus().name(),
                entity.getRecipientName(),
                entity.getRecipientIban(),
                entity.getAmount().toPlainString(),
                entity.getCurrency(),
                entity.getPurpose(),
                entity.getCreatedAt(),
                entity.getSettledAt(),
                entity.getSettlementReference());
    }
}