package com.erudit.content;

import com.erudit.openapi.model.ContentUpsertRequest;
import com.erudit.payment.Subscription;
import com.erudit.payment.SubscriptionRepository;
import com.erudit.payment.SubscriptionStatus;
import com.erudit.user.domain.User;
import com.erudit.user.repository.UserRepository;
import com.erudit.user.service.JwtTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FreemiumAccessIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private ContentService contentService;
    @Autowired private ContentRepository contentRepository;
    @Autowired private ContentProgressRepository progressRepository;
    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtTokenService jwtTokenService;

    private User user;
    private String bearer;
    private Content premium;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.builder().email("premium-" + UUID.randomUUID() + "@example.com")
                .passwordHash("hash").name("Premium learner").build());
        bearer = "Bearer " + jwtTokenService.issueAccessToken(user);
        UUID categoryId = UUID.randomUUID();
        contentRepository.saveCategory(new Category(categoryId, "Premium " + categoryId));
        premium = contentService.create(new ContentUpsertRequest(categoryId,
                ContentUpsertRequest.TypeEnum.ARTICLE, "Premium article")
                .description("Premium description").body("Premium body")
                .mediaUrl("https://example.test/premium.mp4").premiumLocked(true), "admin");
        contentRepository.updateStatus(premium.id(), ContentStatus.PUBLISHED);
    }

    @Test
    void locksPremiumPayloadWithoutSubscriptionAndRevealsItWithActiveSubscription() throws Exception {
        mvc.perform(get("/api/v1/content").param("premium", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].premiumLocked").value(true))
                .andExpect(jsonPath("$.data[0].body").doesNotExist())
                .andExpect(jsonPath("$.data[0].mediaUrl").doesNotExist());
        mvc.perform(get("/api/v1/content/{id}", premium.id()))
                .andExpect(status().isForbidden());

        Subscription active = activeSubscription();
        subscriptionRepository.save(active);
        mvc.perform(get("/api/v1/content").param("premium", "true")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].body").value("Premium body"))
                .andExpect(jsonPath("$.data[0].mediaUrl").value("https://example.test/premium.mp4"));
        mvc.perform(get("/api/v1/content/{id}", premium.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk());
    }

    @Test
    void keepsProgressWhenSubscriptionIsLost() throws Exception {
        Subscription active = activeSubscription();
        subscriptionRepository.save(active);
        mvc.perform(post("/api/v1/content/{id}/view", premium.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk());

        subscriptionRepository.updateStatus(active.id(), SubscriptionStatus.EXPIRED);
        assertThat(progressRepository.find(user.getId().toString(), premium.id())).isPresent();
        mvc.perform(get("/api/v1/content/{id}", premium.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isForbidden());
    }

    private Subscription activeSubscription() {
        Instant now = Instant.now();
        return new Subscription(UUID.randomUUID(), user.getId(), "PREMIUM_MONTHLY",
                SubscriptionStatus.ACTIVE, now.minus(1, ChronoUnit.DAYS), now.plus(30, ChronoUnit.DAYS));
    }
}
