package com.spot.domain.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.spot.domain.goal.DailyGoal;
import com.spot.domain.goal.GoalSource;
import com.spot.domain.group.GroupMember;
import com.spot.domain.group.StudyGroup;
import com.spot.domain.session.SessionSource;
import com.spot.domain.session.StudySession;
import com.spot.domain.user.AuthProvider;
import com.spot.domain.user.User;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
class DashboardServiceTest {

    // 2026-06-27(Fri) 09:00 KST — week Mon 6/23 ~ Fri 6/27
    private static final Instant FIXED_NOW = Instant.parse("2026-06-27T00:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 27);

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private com.spot.domain.user.UserRepository userRepository;

    @Autowired
    private com.spot.domain.group.StudyGroupRepository groupRepository;

    @Autowired
    private com.spot.domain.group.GroupMemberRepository memberRepository;

    @Autowired
    private com.spot.domain.goal.DailyGoalRepository dailyGoalRepository;

    @Autowired
    private com.spot.domain.session.StudySessionRepository sessionRepository;

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        }
    }

    @BeforeEach
    void clean() {
        sessionRepository.deleteAll();
        dailyGoalRepository.deleteAll();
        memberRepository.deleteAll();
        groupRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @Transactional
    void weeklyScoreUsesGoalAchievementAndHourlyPoints() {
        User user = userRepository.save(User.ofSocial(AuthProvider.NAVER, "score-user", null, "Scorer", 60));
        StudyGroup group = groupRepository.save(new StudyGroup("Score Group", "SCOR01", user.getId()));
        memberRepository.save(GroupMember.active(group.getId(), user.getId()));

        // Mon: goal 60, actual 120 -> achieved(3) + 2h(2) = 5
        dailyGoalRepository.save(new DailyGoal(user.getId(), LocalDate.of(2026, 6, 23), 60, GoalSource.USER_SET));
        sessionRepository.save(closedManual(user.getId(), LocalDate.of(2026, 6, 23),
            "2026-06-23T01:00:00Z", "2026-06-23T03:00:00Z", 120));

        // Tue: goal 60, actual 30 -> not achieved + 0h = 0
        dailyGoalRepository.save(new DailyGoal(user.getId(), LocalDate.of(2026, 6, 24), 60, GoalSource.USER_SET));
        sessionRepository.save(closedManual(user.getId(), LocalDate.of(2026, 6, 24),
            "2026-06-24T01:00:00Z", "2026-06-24T01:30:00Z", 30));

        // Wed: goal 0, actual 0 -> achieved(3) + 0h = 3
        dailyGoalRepository.save(new DailyGoal(user.getId(), LocalDate.of(2026, 6, 25), 0, GoalSource.USER_SET));

        var member = dashboardService.getDashboard(user.getId(), group.getId()).members().getFirst();
        assertThat(member.weeklyScoreBreakdown().achievementPoints()).isEqualTo(6); // Mon + Wed
        assertThat(member.weeklyScoreBreakdown().volumeBonus()).isEqualTo(2); // 120min only
        assertThat(member.weeklyScore()).isEqualTo(8);
    }

    private static StudySession closedManual(
        Long userId, LocalDate studyDay, String start, String end, int durationMinutes
    ) {
        return StudySession.manual(
            userId,
            studyDay,
            "Study",
            Instant.parse(start),
            Instant.parse(end)
        );
    }
}
