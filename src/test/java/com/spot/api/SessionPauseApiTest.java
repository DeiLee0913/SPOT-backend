package com.spot.api;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class SessionPauseApiTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-06-27T00:00:00Z");
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
    void timerSessionCanPauseResumeAndEndWithActiveDuration() throws Exception {
        String token = newUserToken();

        MvcResult started = mockMvc.perform(asUser(post("/sessions/start"), token)
                .content("{\"category\":\"Spring\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.status", is("OPEN")))
            .andReturn();
        long sessionId = dataNode(started).get("sessionId").asLong();

        mockMvc.perform(asUser(post("/sessions/" + sessionId + "/pause"), token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status", is("PAUSED")));

        mockMvc.perform(asUser(post("/sessions/" + sessionId + "/resume"), token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status", is("OPEN")));

        mockMvc.perform(asUser(post("/sessions/" + sessionId + "/end"), token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status", is("CLOSED")))
            .andExpect(jsonPath("$.data.durationMinutes", is(0)));
    }

    @Test
    void cannotPauseWhenNotRunning() throws Exception {
        String token = newUserToken();

        MvcResult started = mockMvc.perform(asUser(post("/sessions/start"), token)
                .content("{\"category\":\"JPA\"}"))
            .andExpect(status().isCreated())
            .andReturn();
        long sessionId = dataNode(started).get("sessionId").asLong();

        mockMvc.perform(asUser(post("/sessions/" + sessionId + "/pause"), token))
            .andExpect(status().isOk());

        mockMvc.perform(asUser(post("/sessions/" + sessionId + "/pause"), token))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code", is("SESSION_NOT_RUNNING")));
    }

    private String newUserToken() {
        User user = userRepository.save(User.ofSocial(
            AuthProvider.NAVER,
            "pause-" + SEQ.incrementAndGet(),
            null,
            "학생",
            60
        ));
        return jwtService.generateToken(user.getId());
    }

    private MockHttpServletRequestBuilder asUser(MockHttpServletRequestBuilder builder, String token) {
        return builder
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON);
    }

    private JsonNode dataNode(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }
}
