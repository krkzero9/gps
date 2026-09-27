package com.karthikeyan.paymentrequest.persistence;

import com.karthikeyan.paymentrequest.domain.PaymentRequestStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_request")
public class PaymentRequestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String ownerClientId;
    private String idempotencyKey;
    private String recipientName;
    private String recipientIban;
    private BigDecimal amount;
    private String currency;
    private String purpose;

    @Enumerated(EnumType.STRING)
    private PaymentRequestStatus status;

    private String settlementReference;
    private Instant createdAt;
    private Instant settledAt;

    protected PaymentRequestEntity() {
        // required by JPA
    }

    public PaymentRequestEntity(String ownerClientId, String idempotencyKey,
                                String recipientName, String recipientIban, BigDecimal amount,
                                String currency, String purpose, Instant createdAt) {
        this.ownerClientId = ownerClientId;
        this.idempotencyKey = idempotencyKey;
        this.recipientName = recipientName;
        this.recipientIban = recipientIban;
        this.amount = amount;
        this.currency = currency;
        this.purpose = purpose;
        this.status = PaymentRequestStatus.OPEN;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public String getOwnerClientId() { return ownerClientId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRecipientName() { return recipientName; }
    public String getRecipientIban() { return recipientIban; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getPurpose() { return purpose; }
    public PaymentRequestStatus getStatus() { return status; }
    public String getSettlementReference() { return settlementReference; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getSettledAt() { return settledAt; }
}