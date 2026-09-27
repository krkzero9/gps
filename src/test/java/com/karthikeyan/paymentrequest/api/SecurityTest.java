package com.karthikeyan.paymentrequest.api;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import java.util.List;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityTest {

    private static final String BODY = """
            {
              "recipientName": "Acme GmbH",
              "recipientIban": "DE89370400440532013000",
              "amount": 49.90,
              "currency": "EUR",
              "purpose": "Invoice 2026-0815"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAuthenticationConverter jwtAuthenticationConverter;

    @Test
    void requestWithoutTokenIsRejectedWith401() throws Exception {
        mockMvc.perform(createRequest(UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void businessCannotSettleItsOwnPaymentRequest() throws Exception {
        String id = create(TestTokens.businessA(), UUID.randomUUID().toString());

        mockMvc.perform(post("/api/v1/payment-requests/" + id + "/settle").with(TestTokens.businessA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"settlementReference\": \"BANK-TX-1\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void anotherBusinessCannotSeeThePaymentRequest() throws Exception {
        String id = create(TestTokens.businessA(), UUID.randomUUID().toString());

        mockMvc.perform(get("/api/v1/payment-requests/" + id).with(TestTokens.business(TestTokens.BUSINESS_B)))
                .andExpect(status().isNotFound()) // 404, not 403: don't reveal that the id exists
                .andExpect(jsonPath("$.code").value("PAYMENT_REQUEST_NOT_FOUND"));
    }

    @Test
    void idempotencyKeysAreSeparatePerBusiness() throws Exception {
        String sharedKey = "invoice-" + UUID.randomUUID();

        String idOfA = create(TestTokens.businessA(), sharedKey);
        String idOfB = create(TestTokens.business(TestTokens.BUSINESS_B), sharedKey);

        assertThat(idOfB).isNotEqualTo(idOfA); // same key, different companies: two payment requests
    }

    @Test
    void rolesClaimFromEntraBecomesThePermission() throws Exception {
        String id = create(TestTokens.businessA(), UUID.randomUUID().toString());

        // Like a real Entra token: the permission is only in the "roles" claim, and our converter bean maps it.
        RequestPostProcessor paymentEventsWithRolesClaim = jwt()
                .jwt(token -> token.claim("azp", "payment-events")
                        .claim("roles", List.of("PaymentRequests.Settle")))
                .authorities(token -> jwtAuthenticationConverter.convert(token).getAuthorities());

        mockMvc.perform(post("/api/v1/payment-requests/" + id + "/settle").with(paymentEventsWithRolesClaim)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"settlementReference\": \"BANK-TX-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SETTLED"));
    }

    private MockHttpServletRequestBuilder createRequest(String idempotencyKey) {
        return post("/api/v1/payment-requests")
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY);
    }

    private String create(RequestPostProcessor caller, String idempotencyKey) throws Exception {
        String response = mockMvc.perform(createRequest(idempotencyKey).with(caller))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }
}