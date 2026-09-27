package com.karthikeyan.paymentrequest.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRequestRepository extends JpaRepository<PaymentRequestEntity, UUID> {

    Optional<PaymentRequestEntity> findByOwnerClientIdAndIdempotencyKey(String ownerClientId, String idempotencyKey);

    /** Only the owner finds its payment request; for anyone else it looks like it doesn't exist (404). */
    Optional<PaymentRequestEntity> findByIdAndOwnerClientId(UUID id, String ownerClientId);

    /**
     * Settles the payment request only if it is still OPEN, in one atomic statement.
     * Returns 1 if this call settled it, 0 if it was already settled (or doesn't exist).
     * The database runs concurrent updates of the same row one after another, so exactly one caller gets 1.
     */
    @Modifying(clearAutomatically = true)
    @Query("""
            update PaymentRequestEntity p
               set p.status = com.karthikeyan.paymentrequest.domain.PaymentRequestStatus.SETTLED,
                   p.settlementReference = :settlementReference,
                   p.settledAt = :settledAt
             where p.id = :id
               and p.status = com.karthikeyan.paymentrequest.domain.PaymentRequestStatus.OPEN
            """)
    int settleIfOpen(UUID id, String settlementReference, Instant settledAt);
}