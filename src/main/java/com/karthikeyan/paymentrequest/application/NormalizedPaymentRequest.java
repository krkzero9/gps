package com.karthikeyan.paymentrequest.application;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * A create request after validation, in one canonical form: trimmed text, IBAN without spaces
 * in upper case, amount with exactly two decimals, currency in upper case.
 */
public record NormalizedPaymentRequest(
        String recipientName,
        String recipientIban,
        BigDecimal amount,
        String currency,
        String purpose) {

    /** Call only after validation: setScale(2) fails for amounts with more than two decimals. */
    public static NormalizedPaymentRequest from(CreatePaymentRequestCommand request) {
        return new NormalizedPaymentRequest(
                cleanText(request.recipientName()),
                cleanIban(request.recipientIban()),
                request.amount().setScale(2),
                request.currency().trim().toUpperCase(Locale.ROOT),
                cleanText(request.purpose()));
    }

    /**
     * Removes leading and trailing whitespace, including Unicode spaces such as the non-breaking
     * space (U+00A0), which String.trim() and String.strip() both keep.
     * The validator uses the same method, so "blank" means the same thing in both places.
     */
    static String cleanText(String value) {
        return value.replaceAll("^[\\s\\p{Z}]+|[\\s\\p{Z}]+$", "");
    }

    /** IBANs are often written in groups of four ("DE89 3704 ..."); stored without spaces, in upper case. */
    static String cleanIban(String value) {
        return value.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }
}