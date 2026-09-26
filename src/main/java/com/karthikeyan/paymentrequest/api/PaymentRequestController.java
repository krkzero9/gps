package com.karthikeyan.paymentrequest.api;

import com.karthikeyan.paymentrequest.api.dto.PaymentRequestResponse;
import com.karthikeyan.paymentrequest.application.CreatePaymentRequestCommand;
import com.karthikeyan.paymentrequest.application.CreateResult;
import com.karthikeyan.paymentrequest.application.PaymentRequestService;
import com.karthikeyan.paymentrequest.application.SettleCommand;
import com.karthikeyan.paymentrequest.persistence.PaymentRequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping(PaymentRequestController.BASE_PATH)
public class PaymentRequestController {

    public static final String BASE_PATH = "/api/v1/payment-requests";
    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    public static final String IDEMPOTENT_REPLAYED_HEADER = "Idempotent-Replayed";

    private final PaymentRequestService service;

    public PaymentRequestController(PaymentRequestService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<PaymentRequestResponse> create(
            @RequestHeader(IDEMPOTENCY_KEY_HEADER) String idempotencyKey,
            @RequestBody CreatePaymentRequestCommand request) {
        CreateResult result = service.create(idempotencyKey, request);
        PaymentRequestEntity created = result.paymentRequest();
        URI location = URI.create(BASE_PATH + "/" + created.getId());
        ResponseEntity.BodyBuilder response = ResponseEntity.created(location);
        if (result.replayed()) {
            response.header(IDEMPOTENT_REPLAYED_HEADER, "true");
        }
        return response.body(PaymentRequestResponse.from(created));
    }

    @GetMapping("/{id}")
    public PaymentRequestResponse get(@PathVariable UUID id) {
        return PaymentRequestResponse.from(service.get(id));
    }

    @PostMapping("/{id}/settle")
    public PaymentRequestResponse settle(@PathVariable UUID id, @RequestBody SettleCommand command) {
        return PaymentRequestResponse.from(service.settle(id, command));
    }
}