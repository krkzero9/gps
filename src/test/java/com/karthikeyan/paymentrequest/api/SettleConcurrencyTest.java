package com.karthikeyan.paymentrequest.api;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Extension C: the double-settle race, exercised for real.
 * 10 threads wait at a latch and settle the same OPEN payment request at the same moment.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SettleConcurrencyTest {

    private static final int CALLERS = 10;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void concurrentSettlesWithDifferentReferencesHaveExactlyOneWinner() throws Exception {
        String id = createOpenPaymentRequest();

        List<MvcResult> results = settleConcurrently(id, i -> "BANK-TX-" + i);

        List<String> winners = new ArrayList<>();
        int conflicts = 0;
        for (MvcResult result : results) {
            int status = result.getResponse().getStatus();
            if (status == 200) {
                winners.add(JsonPath.read(result.getResponse().getContentAsString(), "$.settlementReference"));
            } else {
                assertThat(status).isEqualTo(409);
                conflicts++;
            }
        }
        assertThat(winners).hasSize(1);                  // exactly one caller settled it
        assertThat(conflicts).isEqualTo(CALLERS - 1);    // everyone else got 409 ALREADY_SETTLED

        mockMvc.perform(get("/api/v1/payment-requests/" + id).with(TestTokens.businessA()))
                .andExpect(jsonPath("$.status").value("SETTLED"))
                .andExpect(jsonPath("$.settlementReference").value(winners.get(0))); // winner's reference kept
    }

    @Test
    void concurrentSettlesWithTheSameReferenceAllSucceedButSettleOnce() throws Exception {
        String id = createOpenPaymentRequest();

        List<MvcResult> results = settleConcurrently(id, i -> "BANK-TX-SAME");

        List<String> settledAtValues = new ArrayList<>();
        for (MvcResult result : results) {
            assertThat(result.getResponse().getStatus()).isEqualTo(200); // a duplicated event is not an error
            settledAtValues.add(JsonPath.read(result.getResponse().getContentAsString(), "$.settledAt"));
        }
        assertThat(settledAtValues).containsOnly(settledAtValues.get(0)); // settled once: one settledAt for all
    }

    private List<MvcResult> settleConcurrently(String id, IntFunction<String> reference)
            throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(CALLERS);
        CountDownLatch startTogether = new CountDownLatch(1);
        try {
            List<Future<MvcResult>> futures = new ArrayList<>();
            for (int i = 0; i < CALLERS; i++) {
                String body = "{\"settlementReference\": \"" + reference.apply(i) + "\"}";
                futures.add(pool.submit(() -> {
                    startTogether.await();
                    return mockMvc.perform(post("/api/v1/payment-requests/" + id + "/settle").with(TestTokens.paymentEvents())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body)).andReturn();
                }));
            }
            startTogether.countDown();

            List<MvcResult> results = new ArrayList<>();
            for (Future<MvcResult> future : futures) {
                results.add(future.get(10, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private String createOpenPaymentRequest() throws Exception {
        String response = mockMvc.perform(post("/api/v1/payment-requests").with(TestTokens.businessA())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipientName": "Acme GmbH",
                                  "recipientIban": "DE89370400440532013000",
                                  "amount": 49.90,
                                  "currency": "EUR",
                                  "purpose": "Invoice 2026-0815"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }
}