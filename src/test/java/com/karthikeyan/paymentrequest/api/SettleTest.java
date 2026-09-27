package com.karthikeyan.paymentrequest.api;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SettleTest {

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

    @Test
    void settlesAnOpenPaymentRequest() throws Exception {
        String id = createOpenPaymentRequest();

        settle(id, "BANK-TX-1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SETTLED"))
                .andExpect(jsonPath("$.settlementReference").value("BANK-TX-1"))
                .andExpect(jsonPath("$.settledAt").isNotEmpty());

        mockMvc.perform(get("/api/v1/payment-requests/" + id).with(TestTokens.businessA()))
                .andExpect(jsonPath("$.status").value("SETTLED"));
    }

    @Test
    void settlingAgainWithTheSameReferenceIsIdempotent() throws Exception {
        String id = createOpenPaymentRequest();
        String settledAt = JsonPath.read(
                settle(id, "BANK-TX-1").andReturn().getResponse().getContentAsString(), "$.settledAt");

        settle(id, "BANK-TX-1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settledAt").value(settledAt)); // not settled a second time
    }

    @Test
    void settlingAgainWithADifferentReferenceIsRejected() throws Exception {
        String id = createOpenPaymentRequest();
        settle(id, "BANK-TX-1").andExpect(status().isOk());

        settle(id, "BANK-TX-2")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_SETTLED"));

        mockMvc.perform(get("/api/v1/payment-requests/" + id).with(TestTokens.businessA()))
                .andExpect(jsonPath("$.settlementReference").value("BANK-TX-1")); // first one kept
    }

    @Test
    void settlingAnUnknownPaymentRequestReturns404() throws Exception {
        settle(UUID.randomUUID().toString(), "BANK-TX-1")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_REQUEST_NOT_FOUND"));
    }

    private String createOpenPaymentRequest() throws Exception {
        String response = mockMvc.perform(post("/api/v1/payment-requests").with(TestTokens.businessA())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private ResultActions settle(String id, String reference) throws Exception {
        return mockMvc.perform(post("/api/v1/payment-requests/" + id + "/settle").with(TestTokens.paymentEvents())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"settlementReference\": \"" + reference + "\"}"));
    }
}