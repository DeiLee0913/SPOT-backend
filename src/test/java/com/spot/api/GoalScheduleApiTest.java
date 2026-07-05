package com.spot.api;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.spot.auth.jwt.JwtService;
import com.spot.domain.user.AuthProvider;
import com.spot.domain.user.User;
import com.spot.domain.user.UserRepository;
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

@SpringBootTest
@AutoConfigureMockMvc
class GoalScheduleApiTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-06-27T00:00:00Z");
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        }
    }

    @Test
    void setFutureStudyDayGoalAndLoadRange() throws Exception {
        String token = newUserToken("학생");

        mockMvc.perform(asUser(put("/goals/2026-06-28"), token)
                .content("{\"goalMinutes\":90}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.studyDay", is("2026-06-28")))
            .andExpect(jsonPath("$.data.goalMinutes", is(90)))
            .andExpect(jsonPath("$.data.source", is("USER_SET")));

        mockMvc.perform(asUser(put("/goals/2026-06-29"), token)
                .content("{\"goalMinutes\":0}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.goalMinutes", is(0)));

        mockMvc.perform(asUser(get("/goals/range").param("from", "2026-06-27").param("to", "2026-06-30"), token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.from", is("2026-06-27")))
            .andExpect(jsonPath("$.data.to", is("2026-06-30")))
            .andExpect(jsonPath("$.data.goals", hasSize(2)));
    }

    @Test
    void setTodayViaStudyDayPathMatchesTodayEndpoint() throws Exception {
        String token = newUserToken("오늘");

        mockMvc.perform(asUser(put("/goals/2026-06-27"), token)
                .content("{\"goalMinutes\":120}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.goalMinutes", is(120)));

        mockMvc.perform(asUser(get("/goals/today"), token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.goalMinutes", is(120)))
            .andExpect(jsonPath("$.data.source", is("USER_SET")));
    }

    @Test
    void rejectsPastStudyDayGoalChange() throws Exception {
        String token = newUserToken("과거");

        mockMvc.perform(asUser(put("/goals/2026-06-26"), token)
                .content("{\"goalMinutes\":60}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code", is("GOAL_PAST_LOCKED")));
    }

    private String newUserToken(String nickname) {
        String providerId = "goal-" + SEQ.incrementAndGet();
        User user = userRepository.save(User.ofSocial(AuthProvider.NAVER, providerId, null, nickname, 60));
        return jwtService.generateToken(user.getId());
    }

    private MockHttpServletRequestBuilder asUser(MockHttpServletRequestBuilder builder, String token) {
        return builder
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON);
    }
}
