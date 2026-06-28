package com.spot.api;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.spot.auth.jwt.JwtService;
import com.spot.domain.user.AuthProvider;
import com.spot.domain.user.User;
import com.spot.domain.user.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class JoinDayGoalApiTest {

    // 2026-06-27 14:00 KST → study day 2026-06-27, 10:00 마감 이후
    private static final Instant AFTERNOON = Instant.parse("2026-06-27T05:00:00Z");
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private EntityManager entityManager;

    @TestConfiguration
    static class AfternoonClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(AFTERNOON, ZoneOffset.UTC);
        }
    }

    @Test
    void joinDayUserCanSetTodayGoalAfterDeadline() throws Exception {
        String token = newUserToken("신규");

        mockMvc.perform(asUser(get("/me"), token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.needsTodayGoalSetup", is(true)));

        mockMvc.perform(asUser(get("/goals/today"), token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.goalMinutes").value(nullValue()))
            .andExpect(jsonPath("$.data.deadlineApplied", is(true)));

        mockMvc.perform(asUser(put("/goals/today"), token)
                .content("{\"goalMinutes\":120}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.goalMinutes", is(120)))
            .andExpect(jsonPath("$.data.source", is("USER_SET")));

        mockMvc.perform(asUser(get("/me"), token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.needsTodayGoalSetup", is(false)));

        mockMvc.perform(asUser(put("/goals/today"), token)
                .content("{\"goalMinutes\":90}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code", is("GOAL_DEADLINE_PASSED")));
    }

    @Test
    void existingUserCannotSetTodayGoalAfterDeadline() throws Exception {
        User user = userRepository.save(User.ofSocial(
            AuthProvider.NAVER,
            "existing-" + SEQ.incrementAndGet(),
            null,
            "기존",
            60
        ));
        setCreatedAt(user.getId(), Instant.parse("2026-06-26T05:00:00Z"));

        String token = jwtService.generateToken(user.getId());

        mockMvc.perform(asUser(get("/me"), token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.needsTodayGoalSetup", is(false)));

        mockMvc.perform(asUser(put("/goals/today"), token)
                .content("{\"goalMinutes\":120}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code", is("GOAL_DEADLINE_PASSED")));
    }

    private String newUserToken(String nickname) {
        String providerId = "join-day-" + SEQ.incrementAndGet();
        User user = userRepository.save(User.ofSocial(AuthProvider.NAVER, providerId, null, nickname, 120));
        setCreatedAt(user.getId(), AFTERNOON);
        return jwtService.generateToken(user.getId());
    }

    private void setCreatedAt(Long userId, Instant createdAt) {
        entityManager.createNativeQuery("UPDATE users SET created_at = ?1 WHERE id = ?2")
            .setParameter(1, createdAt)
            .setParameter(2, userId)
            .executeUpdate();
        entityManager.flush();
        entityManager.clear();
    }

    private MockHttpServletRequestBuilder asUser(MockHttpServletRequestBuilder builder, String token) {
        return builder
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON);
    }
}
