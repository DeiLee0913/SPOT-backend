package com.spot.api;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
            .andExpect(jsonPath("$.data.goals", hasSize(2)))
            .andExpect(jsonPath("$.data.days", hasSize(4)))
            .andExpect(jsonPath("$.data.days[0].studyDay", is("2026-06-27")))
            .andExpect(jsonPath("$.data.days[0].goalMinutes").value(nullValue()))
            .andExpect(jsonPath("$.data.days[0].actualMinutes", is(0)))
            .andExpect(jsonPath("$.data.days[1].studyDay", is("2026-06-28")))
            .andExpect(jsonPath("$.data.days[1].goalMinutes", is(90)))
            .andExpect(jsonPath("$.data.days[1].actualMinutes", is(0)));
    }

    @Test
    void rangeIncludesClosedSessionActualMinutesPerStudyDay() throws Exception {
        String token = newUserToken("실적");

        mockMvc.perform(asUser(put("/goals/2026-06-28"), token)
                .content("{\"goalMinutes\":90}"))
            .andExpect(status().isOk());

        // study day 2026-06-26 (past): two CLOSED manuals → 45+15=60
        mockMvc.perform(asUser(post("/sessions/manual"), token)
                .content("""
                    {
                      "title":"A",
                      "startedAt":"2026-06-26T01:00:00Z",
                      "endedAt":"2026-06-26T01:45:00Z"
                    }
                    """))
            .andExpect(status().isCreated());
        mockMvc.perform(asUser(post("/sessions/manual"), token)
                .content("""
                    {
                      "title":"B",
                      "startedAt":"2026-06-26T02:00:00Z",
                      "endedAt":"2026-06-26T02:15:00Z"
                    }
                    """))
            .andExpect(status().isCreated());

        // OPEN timer on today must not count toward actualMinutes
        mockMvc.perform(asUser(post("/sessions/start"), token)
                .content("{\"title\":\"Live\"}"))
            .andExpect(status().isCreated());

        mockMvc.perform(asUser(get("/goals/range").param("from", "2026-06-26").param("to", "2026-06-28"), token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.days", hasSize(3)))
            .andExpect(jsonPath("$.data.days[0].studyDay", is("2026-06-26")))
            .andExpect(jsonPath("$.data.days[0].goalMinutes").value(nullValue()))
            .andExpect(jsonPath("$.data.days[0].actualMinutes", is(60)))
            .andExpect(jsonPath("$.data.days[1].studyDay", is("2026-06-27")))
            .andExpect(jsonPath("$.data.days[1].actualMinutes", is(0)))
            .andExpect(jsonPath("$.data.days[2].studyDay", is("2026-06-28")))
            .andExpect(jsonPath("$.data.days[2].goalMinutes", is(90)))
            .andExpect(jsonPath("$.data.days[2].actualMinutes", is(0)))
            .andExpect(jsonPath("$.data.goals", hasSize(1)))
            .andExpect(jsonPath("$.data.goals[0].studyDay", is("2026-06-28")))
            .andExpect(jsonPath("$.data.goals[0].actualMinutes", is(0)));
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
