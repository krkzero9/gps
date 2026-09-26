package com.karthikeyan.paymentrequest.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void reportsAllInvalidFieldsAtOnce() throws Exception {
        String body = """
                {
                  "recipientIban": "DE00 1234",
                  "amount": -5,
                  "currency": "USD",
                  "purpose": "Invoice 2026-0815"
                }
                """;

        create(body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors", hasSize(4)))
                .andExpect(jsonPath("$.errors[*].code", containsInAnyOrder(
                        "FIELD_REQUIRED", "INVALID_IBAN", "AMOUNT_NOT_POSITIVE", "UNSUPPORTED_CURRENCY")));
    }

    @Test
    void rejectsMoreThanTwoDecimalsInsteadOfRounding() throws Exception {
        String body = """
                {
                  "recipientName": "Acme GmbH",
                  "recipientIban": "DE89370400440532013000",
                  "amount": 49.999,
                  "currency": "EUR",
                  "purpose": "Invoice 2026-0815"
                }
                """;

        create(body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("AMOUNT_TOO_MANY_DECIMALS"));
    }

    @Test
    void rejectsControlCharactersAndInvisibleText() throws Exception {
        String body = """
                {
                  "recipientName": "\\u00a0",
                  "recipientIban": "DE89370400440532013000",
                  "amount": 10,
                  "currency": "EUR",
                  "purpose": "Invoice\\u0000"
                }
                """;

        create(body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].code", containsInAnyOrder("FIELD_REQUIRED", "INVALID_CHARACTERS")));
    }

    @Test
    void rejectsIbanWithNonAsciiDigits() throws Exception {
        String body = """
                {
                  "recipientName": "Acme GmbH",
                  "recipientIban": "DE\\uFF18\\uFF19370400440532013000",
                  "amount": 10,
                  "currency": "EUR",
                  "purpose": "Invoice 1"
                }
                """;

        create(body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_IBAN"));
    }

    @Test
    void namesTheFieldWithTheWrongType() throws Exception {
        String body = """
                {
                  "recipientName": "Acme GmbH",
                  "recipientIban": "DE89370400440532013000",
                  "amount": "abc",
                  "currency": "EUR",
                  "purpose": "Invoice 1"
                }
                """;

        create(body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("amount"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_TYPE"));
    }
    @Test
    void rejectsMalformedJson() throws Exception {
        create("{ this is not json")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void requiresIdempotencyKey() throws Exception {
        mockMvc.perform(post("/api/v1/payment-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_MISSING"));
    }

    private ResultActions create(String body) throws Exception {
        return mockMvc.perform(post("/api/v1/payment-requests")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}