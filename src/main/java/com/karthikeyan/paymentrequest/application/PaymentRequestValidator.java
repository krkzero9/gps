package com.karthikeyan.paymentrequest.application;

import com.karthikeyan.paymentrequest.application.exception.ErrorCode;
import com.karthikeyan.paymentrequest.application.exception.FieldViolation;
import com.karthikeyan.paymentrequest.application.exception.PaymentRequestException;
import com.karthikeyan.paymentrequest.application.exception.ValidationException;
import org.iban4j.Iban4jException;
import org.iban4j.IbanUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class PaymentRequestValidator {

    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    /** ASCII only: country code, check digits, 11-30 letters/digits. iban4j alone accepts e.g. full-width digits. */
    private static final Pattern IBAN_FORMAT = Pattern.compile("[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}");
    private static final Pattern CONTROL_CHARACTER = Pattern.compile("\\p{Cc}");
    private static final String SUPPORTED_CURRENCY = "EUR";
    private static final int MAX_NAME_LENGTH = 70;
    private static final int MAX_PURPOSE_LENGTH = 140;
    private static final int MAX_SETTLEMENT_REFERENCE_LENGTH = 64;

    private final BigDecimal maxAmount;

    public PaymentRequestValidator(@Value("${payment-request.max-amount}") BigDecimal maxAmount) {
        this.maxAmount = maxAmount;
    }

    public void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || !IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {
            throw new PaymentRequestException(ErrorCode.IDEMPOTENCY_KEY_INVALID,
                    "Idempotency-Key must be 1-64 characters of letters, digits, '-' or '_'.");
        }
    }

    public void validate(CreatePaymentRequestCommand request) {
        List<FieldViolation> violations = new ArrayList<>();
        checkText("recipientName", request.recipientName(), MAX_NAME_LENGTH, violations);
        checkIban(request.recipientIban(), violations);
        checkAmount(request.amount(), violations);
        checkCurrency(request.currency(), violations);
        checkText("purpose", request.purpose(), MAX_PURPOSE_LENGTH, violations);
        if (!violations.isEmpty()) {
            throw new ValidationException(violations);
        }
    }

    public void validate(SettleCommand command) {
        List<FieldViolation> violations = new ArrayList<>();
        checkText("settlementReference", command.settlementReference(), MAX_SETTLEMENT_REFERENCE_LENGTH, violations);
        if (!violations.isEmpty()) {
            throw new ValidationException(violations);
        }
    }

    private void checkText(String field, String value, int maxLength, List<FieldViolation> violations) {
        if (value == null || NormalizedPaymentRequest.cleanText(value).isEmpty()) {
            violations.add(new FieldViolation(field, "FIELD_REQUIRED", field + " is required."));
        } else if (CONTROL_CHARACTER.matcher(value).find()) {
            violations.add(new FieldViolation(field, "INVALID_CHARACTERS",
                    field + " must not contain control characters."));
        } else if (NormalizedPaymentRequest.cleanText(value).length() > maxLength) {
            violations.add(new FieldViolation(field, "TEXT_TOO_LONG",
                    field + " must be at most " + maxLength + " characters."));
        }
    }

    private void checkIban(String value, List<FieldViolation> violations) {
        if (value == null || value.isBlank()) {
            violations.add(new FieldViolation("recipientIban", "FIELD_REQUIRED", "recipientIban is required."));
            return;
        }
        String iban = NormalizedPaymentRequest.cleanIban(value);
        if (!IBAN_FORMAT.matcher(iban).matches() || !passesIbanCheck(iban)) {
            violations.add(new FieldViolation("recipientIban", "INVALID_IBAN", "recipientIban is not a valid IBAN."));
        }
    }

    /** Country-specific length and the mod-97 checksum, done by iban4j. */
    private static boolean passesIbanCheck(String iban) {
        try {
            IbanUtil.validate(iban);
            return true;
        } catch (Iban4jException e) {
            return false;
        }
    }

    private void checkAmount(BigDecimal amount, List<FieldViolation> violations) {
        if (amount == null) {
            violations.add(new FieldViolation("amount", "FIELD_REQUIRED", "amount is required."));
        } else if (amount.signum() <= 0) {
            violations.add(new FieldViolation("amount", "AMOUNT_NOT_POSITIVE", "amount must be greater than 0."));
        } else if (amount.stripTrailingZeros().scale() > 2) {
            violations.add(new FieldViolation("amount", "AMOUNT_TOO_MANY_DECIMALS",
                    "amount must have at most 2 decimal places."));
        } else if (amount.compareTo(maxAmount) > 0) {
            violations.add(new FieldViolation("amount", "AMOUNT_TOO_LARGE",
                    "amount must not exceed " + maxAmount.toPlainString() + "."));
        }
    }

    private void checkCurrency(String currency, List<FieldViolation> violations) {
        if (currency == null || currency.isBlank()) {
            violations.add(new FieldViolation("currency", "FIELD_REQUIRED", "currency is required."));
        } else if (!SUPPORTED_CURRENCY.equalsIgnoreCase(currency.trim())) {
            violations.add(new FieldViolation("currency", "UNSUPPORTED_CURRENCY", "Only EUR is supported."));
        }
    }
}