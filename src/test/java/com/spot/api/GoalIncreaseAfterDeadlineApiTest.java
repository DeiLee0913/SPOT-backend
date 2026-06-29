package com.spot.api;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.spot.auth.jwt.JwtService;
import com.spot.domain.goal.DailyGoal;
import com.spot.domain.goal.DailyGoalRepository;
import com.spot.domain.goal.GoalSource;
import com.spot.domain.user.AuthProvider;
import com.spot.domain.user.User;
import com.spot.domain.user.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
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
class GoalIncreaseAfterDeadlineApiTest {

    // 2026-06-27 14:00 KST — study day 2026-06-27, after 11:00 deadline
    private static final Instant AFTERNOON = Instant.parse("2026-06-27T05:00:00Z");
    private static final LocalDate STUDY_DAY = LocalDate.of(2026, 6, 27);
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DailyGoalRepository dailyGoalRepository;

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

    private String token;
    private User user;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.ofSocial(
            AuthProvider.NAVER,
            "goal-inc-" + SEQ.incrementAndGet(),
            null,
            "학습자",
            120
        ));
        setCreatedAt(user.getId(), Instant.parse("2026-06-20T05:00:00Z"));
        token = jwtService.generateToken(user.getId());
    }

    @Test
    void canIncreaseGoalAfterDeadlineWhenAchieved() throws Exception {
        dailyGoalRepository.save(new DailyGoal(user.getId(), STUDY_DAY, 120, GoalSource.USER_SET));
        registerManualSession(120);

        mockMvc.perform(asUser(put("/goals/today"), token)
                .content("{\"goalMinutes\":180}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.goalMinutes", is(180)))
            .andExpect(jsonPath("$.data.source", is("USER_SET")));
    }

    @Test
    void cannotDecreaseGoalAfterDeadlineEvenWhenAchieved() throws Exception {
        dailyGoalRepository.save(new DailyGoal(user.getId(), STUDY_DAY, 120, GoalSource.USER_SET));
        registerManualSession(120);

        mockMvc.perform(asUser(put("/goals/today"), token)
                .content("{\"goalMinutes\":90}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code", is("GOAL_CANNOT_DECREASE")));
    }

    @Test
    void cannotIncreaseGoalAfterDeadlineWhenNotAchieved() throws Exception {
        dailyGoalRepository.save(new DailyGoal(user.getId(), STUDY_DAY, 120, GoalSource.USER_SET));
        registerManualSession(60);

        mockMvc.perform(asUser(put("/goals/today"), token)
                .content("{\"goalMinutes\":180}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code", is("GOAL_NOT_ACHIEVED")));
    }

    @Test
    void cannotSetInitialGoalAfterDeadlineWithoutAchievement() throws Exception {
        mockMvc.perform(asUser(put("/goals/today"), token)
                .content("{\"goalMinutes\":120}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code", is("GOAL_NOT_ACHIEVED")));
    }

    @Test
    void canIncreaseDefaultAppliedGoalAfterDeadlineWhenAchieved() throws Exception {
        dailyGoalRepository.save(new DailyGoal(user.getId(), STUDY_DAY, 120, GoalSource.DEFAULT_APPLIED));
        registerManualSession(120);

        mockMvc.perform(asUser(put("/goals/today"), token)
                .content("{\"goalMinutes\":150}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.goalMinutes", is(150)))
            .andExpect(jsonPath("$.data.source", is("USER_SET")));
    }

    private void registerManualSession(int durationMinutes) throws Exception {
        Instant start = Instant.parse("2026-06-27T00:00:00Z");
        Instant end = start.plusSeconds(durationMinutes * 60L);
        mockMvc.perform(asUser(post("/sessions/manual"), token)
                .content(String.format(
                    "{\"title\":\"Study\",\"startedAt\":\"%s\",\"endedAt\":\"%s\"}",
                    start, end
                )))
            .andExpect(status().isCreated());
    }

    private void setCreatedAt(Long userId, Instant createdAt) {
        entityManager.createNativeQuery("UPDATE users SET created_at = ?1 WHERE id = ?2")
            .setParameter(1, createdAt)
            .setParameter(2, userId)
            .executeUpdate();
        entityManager.flush();
        entityManager.clear();
        user = userRepository.findById(userId).orElseThrow();
    }

    private MockHttpServletRequestBuilder asUser(MockHttpServletRequestBuilder builder, String token) {
        return builder
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON);
    }
}
