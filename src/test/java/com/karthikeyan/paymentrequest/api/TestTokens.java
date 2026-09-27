package com.karthikeyan.paymentrequest.api;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/**
 * Ready-made callers for MockMvc tests. spring-security-test puts an authenticated JWT on the request,
 * so the tests need no real token and never contact Entra ID (they run offline).
 */
final class TestTokens {

    static final String BUSINESS_A = "business-a";
    static final String BUSINESS_B = "business-b";

    private TestTokens() {
    }

    /** A company that creates and reads its own payment requests. */
    static JwtRequestPostProcessor business(String clientId) {
        return jwt().jwt(token -> token.subject(clientId).claim("azp", clientId))
                .authorities(new SimpleGrantedAuthority("PaymentRequests.Write"),
                        new SimpleGrantedAuthority("PaymentRequests.Read"));
    }

    static JwtRequestPostProcessor businessA() {
        return business(BUSINESS_A);
    }

    /** The payment side, which reports that money arrived. */
    static JwtRequestPostProcessor paymentEvents() {
        return jwt().jwt(token -> token.subject("payment-events").claim("azp", "payment-events"))
                .authorities(new SimpleGrantedAuthority("PaymentRequests.Settle"));
    }
}