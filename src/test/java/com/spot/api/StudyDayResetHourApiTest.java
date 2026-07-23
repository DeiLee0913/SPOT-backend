package com.spot.api;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
class StudyDayResetHourApiTest {

    /** 2026-06-27 05:30 KST — default reset(06:00)이면 전일, reset(0)이면 당일 */
    private static final Instant FIXED_NOW = Instant.parse("2026-06-26T20:30:00Z");
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        }
    }

    @Test
    void meExposesResetHourAndCurrentStudyDay() throws Exception {
        String token = newUserToken();

        mockMvc.perform(asUser(get("/me"), token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.studyDayResetHour", is(6)))
            .andExpect(jsonPath("$.data.currentStudyDay", is("2026-06-26")));
    }

    @Test
    void updateResetHourChangesCurrentStudyDayAndRejectsOutOfRange() throws Exception {
        String token = newUserToken();

        mockMvc.perform(asUser(put("/me/study-day-reset-hour"), token)
                .content("{\"studyDayResetHour\":0}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.studyDayResetHour", is(0)))
            .andExpect(jsonPath("$.data.currentStudyDay", is("2026-06-27")));

        mockMvc.perform(asUser(put("/me/study-day-reset-hour"), token)
                .content("{\"studyDayResetHour\":23}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.studyDayResetHour", is(23)))
            .andExpect(jsonPath("$.data.currentStudyDay", is("2026-06-26")));

        mockMvc.perform(asUser(put("/me/study-day-reset-hour"), token)
                .content("{\"studyDayResetHour\":24}"))
            .andExpect(status().isBadRequest());

        mockMvc.perform(asUser(put("/me/study-day-reset-hour"), token)
                .content("{\"studyDayResetHour\":-1}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void sessionStartUsesAccountResetHourForStudyDay() throws Exception {
        String token = newUserToken();

        // default reset=6 at 05:30 KST → studyDay 2026-06-26
        mockMvc.perform(asUser(post("/sessions/start"), token).content("{}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.studyDay", is("2026-06-26")));

        mockMvc.perform(asUser(post("/sessions/" + sessionId(token) + "/end"), token))
            .andExpect(status().isOk());

        mockMvc.perform(asUser(put("/me/study-day-reset-hour"), token)
                .content("{\"studyDayResetHour\":0}"))
            .andExpect(status().isOk());

        mockMvc.perform(asUser(post("/sessions/start"), token).content("{}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.studyDay", is("2026-06-27")));
    }

    @Test
    void sessionsListDefaultsToCurrentStudyDayForAccount() throws Exception {
        String token = newUserToken();

        mockMvc.perform(asUser(put("/me/study-day-reset-hour"), token)
                .content("{\"studyDayResetHour\":0}"))
            .andExpect(status().isOk());

        mockMvc.perform(asUser(post("/sessions/manual"), token)
                .content("""
                    {
                      "title":"Past block",
                      "startedAt":"2026-06-26T10:00:00Z",
                      "endedAt":"2026-06-26T11:00:00Z"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.studyDay", is("2026-06-26")));

        mockMvc.perform(asUser(get("/sessions"), token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()", is(0)));

        mockMvc.perform(asUser(get("/sessions").param("studyDay", "2026-06-26"), token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()", is(1)));
    }

    @Test
    void goalPastLockUsesAccountCurrentStudyDay() throws Exception {
        String token = newUserToken();

        // reset=6 → today 2026-06-26, so 2026-06-25 is past
        mockMvc.perform(asUser(put("/goals/2026-06-25"), token)
                .content("{\"goalMinutes\":60}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code", is("GOAL_PAST_LOCKED")));

        mockMvc.perform(asUser(put("/me/study-day-reset-hour"), token)
                .content("{\"studyDayResetHour\":0}"))
            .andExpect(status().isOk());

        // reset=0 → today 2026-06-27, 2026-06-26 is past, 2026-06-27 ok
        mockMvc.perform(asUser(put("/goals/2026-06-26"), token)
                .content("{\"goalMinutes\":60}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code", is("GOAL_PAST_LOCKED")));

        mockMvc.perform(asUser(put("/goals/2026-06-27"), token)
                .content("{\"goalMinutes\":90}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.studyDay", is("2026-06-27")))
            .andExpect(jsonPath("$.data.goalMinutes", is(90)));
    }

    private long sessionId(String token) throws Exception {
        MvcResult open = mockMvc.perform(asUser(get("/sessions/open"), token))
            .andExpect(status().isOk())
            .andReturn();
        return dataNode(open).get("sessionId").asLong();
    }

    private JsonNode dataNode(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private String newUserToken() {
        User user = userRepository.save(User.ofSocial(
            AuthProvider.NAVER,
            "reset-" + SEQ.incrementAndGet(),
            null,
            "리셋",
            60
        ));
        return jwtService.generateToken(user.getId());
    }

    private MockHttpServletRequestBuilder asUser(MockHttpServletRequestBuilder builder, String token) {
        return builder
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON);
    }
}
