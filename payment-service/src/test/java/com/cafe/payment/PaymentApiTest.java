package com.cafe.payment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:payment-test;MODE=MySQL")
@AutoConfigureMockMvc
class PaymentApiTest {
    @Autowired
    MockMvc mvc;

    @Test
    void successAndFailureAreStoredAndQueryable() throws Exception {
        pay("success", 4000, "SUCCESS").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
        pay("declined", 8000, "FAILURE").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").value("SIMULATED_DECLINE"));
        mvc.perform(get("/api/payments/declined")).andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(8000))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.processedAt").isString());
        mvc.perform(get("/api/payments")).andExpect(status().isOk()).andExpect(jsonPath("$").isArray());
        mvc.perform(get("/")).andExpect(status().isNotFound());
        mvc.perform(get("/index.html")).andExpect(status().isNotFound());
    }

    @Test
    void retriesReturnOriginalResultAndRejectChangedAmount() throws Exception {
        pay("retry", 4000, "FAILURE").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FAILED"));
        pay("retry", 4000, "SUCCESS").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FAILED"));
        pay("retry", 5000, "SUCCESS").andExpect(status().isConflict());
        pay("invalid", 0, "SUCCESS").andExpect(status().isBadRequest());
    }

    private org.springframework.test.web.servlet.ResultActions pay(String orderId, long amount, String simulation)
            throws Exception {
        String payload = """
                {"orderId":"%s","amount":%d,"simulate":"%s"}
                """.formatted(orderId, amount, simulation);
        return mvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON).content(payload));
    }
}
