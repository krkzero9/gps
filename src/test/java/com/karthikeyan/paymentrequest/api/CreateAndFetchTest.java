package com.karthikeyan.paymentrequest.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CreateAndFetchTest {

    private static final String VALID_BODY = """
            {
              "recipientName": "Acme GmbH",
              "recipientIban": "de89 3704 0044 0532 0130 00",
              "amount": 49.9,
              "currency": "eur",
              "purpose": "Invoice 2026-0815"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createsOpenPaymentRequestAndFetchesIt() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/payment-requests")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern("/api/v1/payment-requests/[0-9a-f-]{36}")))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.amount").value("49.90"))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.recipientIban").value("DE89370400440532013000"))
                .andReturn();

        String location = created.getResponse().getHeader("Location");

        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.recipientName").value("Acme GmbH"))
                .andExpect(jsonPath("$.amount").value("49.90"))
                .andExpect(jsonPath("$.purpose").value("Invoice 2026-0815"));
    }

    @Test
    void unknownIdReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/payment-requests/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_REQUEST_NOT_FOUND"));
    }
}