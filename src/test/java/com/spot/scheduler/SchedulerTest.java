package com.spot.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import com.spot.domain.goal.DailyGoal;
import com.spot.domain.goal.DailyGoalRepository;
import com.spot.domain.goal.GoalService;
import com.spot.domain.goal.GoalSource;
import com.spot.domain.session.SessionService;
import com.spot.domain.session.SessionStatus;
import com.spot.domain.session.StudySession;
import com.spot.domain.session.StudySessionRepository;
import com.spot.domain.user.AuthProvider;
import com.spot.domain.user.User;
import com.spot.domain.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@SpringBootTest
class SchedulerTest {

    // 2026-06-27 09:00 KST → 현재 study day 2026-06-27, 11:00 마감 이전
    private static final Instant FIXED_NOW = Instant.parse("2026-06-27T00:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 27);

    @Autowired
    private GoalService goalService;

    @Autowired
    private SessionService sessionService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DailyGoalRepository dailyGoalRepository;

    @Autowired
    private StudySessionRepository sessionRepository;

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        }
    }

    @Test
    void applyDefaultGoalsKeepsUserSetAndSnapshotsDefaultForOthers() {
        User userSet = userRepository.save(User.ofSocial(AuthProvider.NAVER, "sched-set", null, "직접설정", 60));
        User userDefault = userRepository.save(User.ofSocial(AuthProvider.NAVER, "sched-default", null, "기본적용", 90));

        goalService.setToday(userSet.getId(), 120);

        goalService.applyDefaultGoals(TODAY);

        DailyGoal setGoal = dailyGoalRepository.findByUserIdAndStudyDay(userSet.getId(), TODAY).orElseThrow();
        assertThat(setGoal.getGoalMinutes()).isEqualTo(120);
        assertThat(setGoal.getSource()).isEqualTo(GoalSource.USER_SET);

        DailyGoal defaultGoal = dailyGoalRepository.findByUserIdAndStudyDay(userDefault.getId(), TODAY).orElseThrow();
        assertThat(defaultGoal.getGoalMinutes()).isEqualTo(90);
        assertThat(defaultGoal.getSource()).isEqualTo(GoalSource.DEFAULT_APPLIED);

        // 재실행해도 중복 생성되지 않는다
        goalService.applyDefaultGoals(TODAY);
        long count = dailyGoalRepository.findByUserIdAndStudyDayBetween(userDefault.getId(), TODAY, TODAY).size();
        assertThat(count).isEqualTo(1);
    }

    @Test
    void closeCrossDaySessionsClosesStaleOpenSessionsAtBoundary() {
        User owner = userRepository.save(User.ofSocial(AuthProvider.NAVER, "sched-session", null, "세션주인", 60));

        // 전일(2026-06-26) study day의 OPEN 세션
        Instant startedAt = Instant.parse("2026-06-26T01:00:00Z"); // 2026-06-26 10:00 KST
        StudySession stale = sessionRepository.save(
            StudySession.openTimer(owner.getId(), LocalDate.of(2026, 6, 26), null, startedAt));

        // 오늘(2026-06-27) study day의 OPEN 세션 — 유지되어야 함
        StudySession current = sessionRepository.save(
            StudySession.openTimer(owner.getId(), TODAY, null, Instant.parse("2026-06-26T22:00:00Z")));

        int closed = sessionService.closeCrossDaySessions();
        assertThat(closed).isEqualTo(1);

        StudySession reloadedStale = sessionRepository.findById(stale.getId()).orElseThrow();
        assertThat(reloadedStale.getStatus()).isEqualTo(SessionStatus.CLOSED);
        // 경계 = 2026-06-27 06:00 KST = 2026-06-26T21:00:00Z
        assertThat(reloadedStale.getEndedAt()).isEqualTo(Instant.parse("2026-06-26T21:00:00Z"));
        assertThat(reloadedStale.getDurationMinutes()).isEqualTo(20 * 60);

        StudySession reloadedCurrent = sessionRepository.findById(current.getId()).orElseThrow();
        assertThat(reloadedCurrent.getStatus()).isEqualTo(SessionStatus.OPEN);
    }
}
