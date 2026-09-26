package com.karthikeyan.paymentrequest.application;

import com.karthikeyan.paymentrequest.application.exception.ErrorCode;
import com.karthikeyan.paymentrequest.application.exception.PaymentRequestException;
import com.karthikeyan.paymentrequest.persistence.PaymentRequestEntity;
import com.karthikeyan.paymentrequest.persistence.PaymentRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class PaymentRequestService {

    private final PaymentRequestRepository repository;
    private final PaymentRequestValidator validator;

    public PaymentRequestService(PaymentRequestRepository repository, PaymentRequestValidator validator) {
        this.repository = repository;
        this.validator = validator;
    }

    public PaymentRequestEntity create(String idempotencyKey, CreatePaymentRequestCommand request) {
        validator.validateIdempotencyKey(idempotencyKey);
        validator.validate(request);
        NormalizedPaymentRequest normalized = NormalizedPaymentRequest.from(request);

        PaymentRequestEntity entity = new PaymentRequestEntity(
                idempotencyKey,
                normalized.recipientName(),
                normalized.recipientIban(),
                normalized.amount(),
                normalized.currency(),
                normalized.purpose(),
                Instant.now().truncatedTo(ChronoUnit.MILLIS));
        return repository.save(entity);
    }

    @Transactional(readOnly = true)
    public PaymentRequestEntity get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new PaymentRequestException(ErrorCode.PAYMENT_REQUEST_NOT_FOUND,
                        "Payment request " + id + " was not found."));
    }
}