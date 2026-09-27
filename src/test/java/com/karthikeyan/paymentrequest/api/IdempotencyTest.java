package com.karthikeyan.paymentrequest.api;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class IdempotencyTest {

    private static final String BODY = """
            {
              "recipientName": "Acme GmbH",
              "recipientIban": "DE89370400440532013000",
              "amount": 49.9,
              "currency": "EUR",
              "purpose": "Invoice 2026-0815"
            }
            """;

    /** Written differently, but the same payment after normalization. */
    private static final String SAME_PAYMENT_WRITTEN_DIFFERENTLY = """
            {
              "recipientName": "  Acme GmbH ",
              "recipientIban": "de89 3704 0044 0532 0130 00",
              "amount": 49.90,
              "currency": "eur",
              "purpose": "Invoice 2026-0815"
            }
            """;

    private static final String DIFFERENT_AMOUNT = """
            {
              "recipientName": "Acme GmbH",
              "recipientIban": "DE89370400440532013000",
              "amount": 99.00,
              "currency": "EUR",
              "purpose": "Invoice 2026-0815"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void sameKeyAndSameRequestReplaysTheOriginal() throws Exception {
        String key = UUID.randomUUID().toString();

        MvcResult first = create(key, BODY)
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Idempotent-Replayed"))
                .andReturn();
        String id = JsonPath.read(first.getResponse().getContentAsString(), "$.id");

        create(key, SAME_PAYMENT_WRITTEN_DIFFERENTLY)
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(header().string("Location", "/api/v1/payment-requests/" + id))
                .andExpect(jsonPath("$.id").value(id));
    }

    @Test
    void sameKeyWithDifferentRequestIsRejected() throws Exception {
        String key = UUID.randomUUID().toString();
        create(key, BODY).andExpect(status().isCreated());

        create(key, DIFFERENT_AMOUNT)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void concurrentRequestsWithTheSameKeyCreateExactlyOne() throws Exception {
        String key = UUID.randomUUID().toString();
        int callers = 10;
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        CountDownLatch startTogether = new CountDownLatch(1);

        List<Future<MvcResult>> futures = new ArrayList<>();
        for (int i = 0; i < callers; i++) {
            futures.add(pool.submit(() -> {
                startTogether.await();
                return create(key, BODY).andReturn();
            }));
        }
        startTogether.countDown();

        Set<String> ids = new HashSet<>();
        int replayed = 0;
        try {
            for (Future<MvcResult> future : futures) {
                MvcResult result = future.get(10, TimeUnit.SECONDS);
                assertThat(result.getResponse().getStatus()).isEqualTo(201);
                ids.add(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
                if ("true".equals(result.getResponse().getHeader("Idempotent-Replayed"))) {
                    replayed++;
                }
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(ids).hasSize(1);                 // every caller got the same payment request
        assertThat(replayed).isEqualTo(callers - 1); // exactly one created it, the others got a replay
    }

    private ResultActions create(String key, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/payment-requests").with(TestTokens.businessA())
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}