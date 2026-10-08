package com.erudit.payment;

import com.erudit.payment.repository.SubscriptionPlanRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SubscriptionPlanCatalogIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private SubscriptionPlanRepository repository;

    @Test
    void returnsActivePlansInStableOrderAndHidesArchivedPlans() throws Exception {
        mvc.perform(get("/api/v1/subscriptions/plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].code").value("PREMIUM_MONTHLY"))
                .andExpect(jsonPath("$.data[0].price").value("299.00"))
                .andExpect(jsonPath("$.data[0].currency").value("RUB"))
                .andExpect(jsonPath("$.data[0].period").value("MONTH"))
                .andExpect(jsonPath("$.data[1].code").value("PREMIUM_YEARLY"));

        repository.archive("PREMIUM_MONTHLY");
        mvc.perform(get("/api/v1/subscriptions/plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].code").value("PREMIUM_YEARLY"));

        repository.archive("PREMIUM_YEARLY");
        mvc.perform(get("/api/v1/subscriptions/plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }
}
