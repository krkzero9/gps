package com.karthikeyan.paymentrequest.application;

import com.karthikeyan.paymentrequest.application.exception.ErrorCode;
import com.karthikeyan.paymentrequest.application.exception.PaymentRequestException;
import com.karthikeyan.paymentrequest.persistence.PaymentRequestEntity;
import com.karthikeyan.paymentrequest.persistence.PaymentRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentRequestService {

    private static final Logger log = LoggerFactory.getLogger(PaymentRequestService.class);

    private final PaymentRequestRepository repository;
    private final PaymentRequestValidator validator;

    public PaymentRequestService(PaymentRequestRepository repository, PaymentRequestValidator validator) {
        this.repository = repository;
        this.validator = validator;
    }

    /**
     * Creates a payment request, or replays the existing one when the same Idempotency-Key
     * arrives again with the same content.
     * <p>
     * Deliberately NOT @Transactional, for two reasons:
     * <ol>
     *   <li>Hibernate sends the INSERT only when the transaction commits. With one transaction around
     *       this method, the duplicate-key error would appear after the method returns, outside the
     *       try/catch below, and become a 500.</li>
     *   <li>After a failed insert the transaction can't be used any more (PostgreSQL aborts it),
     *       so the lookup of the winner must run in a new one.</li>
     * </ol>
     * Without @Transactional, each repository call runs and commits in its own transaction.
     */
    public CreateResult create(String ownerClientId, String idempotencyKey, CreatePaymentRequestCommand command) {
        validator.validateIdempotencyKey(idempotencyKey);
        validator.validate(command);
        NormalizedPaymentRequest request = NormalizedPaymentRequest.from(command);

        Optional<PaymentRequestEntity> existing =
                repository.findByOwnerClientIdAndIdempotencyKey(ownerClientId, idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get(), request);
        }
        try {
            PaymentRequestEntity created = repository.save(newEntity(ownerClientId, idempotencyKey, request));
            log.info("Created payment request {}", created.getId());
            return new CreateResult(created, false);
        } catch (DataIntegrityViolationException e) {
            // Lost a race: a request with the same key was inserted between our lookup and our insert.
            // The UNIQUE constraint stopped the duplicate; now answer like any other repeated request.
            PaymentRequestEntity winner = repository.findByOwnerClientIdAndIdempotencyKey(ownerClientId, idempotencyKey)
                    .orElseThrow(() -> e);
            log.info("Concurrent create with the same Idempotency-Key; payment request {} won", winner.getId());
            return replay(winner, request);
        }
    }

    /** Returns the payment request only to its owner; any other client gets 404, as if it didn't exist. */
    @Transactional(readOnly = true)
    public PaymentRequestEntity get(UUID id, String ownerClientId) {
        return repository.findByIdAndOwnerClientId(id, ownerClientId).orElseThrow(() -> notFound(id));
    }

    /**
     * Settles an OPEN payment request exactly once.
     * The conditional UPDATE decides the winner inside the database, so this is safe with several
     * service instances. Repeating the call with the same reference is idempotent (a retried event);
     * a different reference means someone else already settled it.
     */
    @Transactional
    public PaymentRequestEntity settle(UUID id, SettleCommand command) {
        validator.validate(command);
        String reference = NormalizedPaymentRequest.cleanText(command.settlementReference());

        int updated = repository.settleIfOpen(id, reference, Instant.now().truncatedTo(ChronoUnit.MILLIS));
        PaymentRequestEntity paymentRequest = repository.findById(id).orElseThrow(() -> notFound(id));

        if (updated == 1) {
            log.info("Settled payment request {}", id);
            return paymentRequest;
        }
        if (reference.equals(paymentRequest.getSettlementReference())) {
            log.info("Payment request {} was already settled with the same reference", id);
            return paymentRequest;
        }
        log.warn("Payment request {} is already settled with a different reference", id);
        throw new PaymentRequestException(ErrorCode.ALREADY_SETTLED,
                "Payment request " + id + " is already settled.");
    }

    private static PaymentRequestException notFound(UUID id) {
        return new PaymentRequestException(ErrorCode.PAYMENT_REQUEST_NOT_FOUND,
                "Payment request " + id + " was not found.");
    }

    private static CreateResult replay(PaymentRequestEntity existing, NormalizedPaymentRequest request) {
        if (!isSameRequest(existing, request)) {
            log.warn("Idempotency-Key of payment request {} was reused with a different request", existing.getId());
            throw new PaymentRequestException(ErrorCode.IDEMPOTENCY_KEY_REUSED,
                    "This Idempotency-Key was already used with a different request.");
        }
        log.info("Replayed payment request {} for a repeated Idempotency-Key", existing.getId());
        return new CreateResult(existing, true);
    }

    private static boolean isSameRequest(PaymentRequestEntity existing, NormalizedPaymentRequest request) {
        return existing.getRecipientName().equals(request.recipientName())
                && existing.getRecipientIban().equals(request.recipientIban())
                && existing.getAmount().compareTo(request.amount()) == 0   // 49.9 equals 49.90
                && existing.getCurrency().equals(request.currency())
                && existing.getPurpose().equals(request.purpose());
    }

    private static PaymentRequestEntity newEntity(String ownerClientId, String idempotencyKey,
                                                  NormalizedPaymentRequest request) {
        return new PaymentRequestEntity(
                ownerClientId,
                idempotencyKey,
                request.recipientName(),
                request.recipientIban(),
                request.amount(),
                request.currency(),
                request.purpose(),
                Instant.now().truncatedTo(ChronoUnit.MILLIS));
    }
}