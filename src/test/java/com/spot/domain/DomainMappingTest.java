package com.spot.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.spot.domain.goal.DailyGoal;
import com.spot.domain.goal.DailyGoalRepository;
import com.spot.domain.goal.GoalSource;
import com.spot.domain.group.GroupMember;
import com.spot.domain.group.GroupMemberRepository;
import com.spot.domain.group.MemberStatus;
import com.spot.domain.group.StudyGroup;
import com.spot.domain.group.StudyGroupRepository;
import com.spot.domain.session.SessionStatus;
import com.spot.domain.session.StudySession;
import com.spot.domain.session.StudySessionRepository;
import com.spot.domain.user.AuthProvider;
import com.spot.domain.user.User;
import com.spot.domain.user.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DomainMappingTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudyGroupRepository studyGroupRepository;

    @Autowired
    private GroupMemberRepository groupMemberRepository;

    @Autowired
    private DailyGoalRepository dailyGoalRepository;

    @Autowired
    private StudySessionRepository studySessionRepository;

    @Test
    void persistsUserAndFindsByProvider() {
        User user = userRepository.save(User.ofSocial(AuthProvider.NAVER, "naver-1", "minji@example.com", "민지", 120));

        assertThat(user.getId()).isNotNull();
        assertThat(user.getCreatedAt()).isNotNull();
        assertThat(user.getUpdatedAt()).isNotNull();
        assertThat(userRepository.findByProviderAndProviderId(AuthProvider.NAVER, "naver-1")).isPresent();
    }

    @Test
    void persistsGroupAndMembership() {
        User creator = userRepository.save(User.ofSocial(AuthProvider.NAVER, "naver-2", null, "준호", 90));
        StudyGroup group = studyGroupRepository.save(new StudyGroup("IT 스터디", "ABC123", creator.getId()));

        groupMemberRepository.save(GroupMember.active(group.getId(), creator.getId()));

        assertThat(studyGroupRepository.findByInviteCode("ABC123")).isPresent();
        assertThat(group.getMaxMembers()).isEqualTo(StudyGroup.DEFAULT_MAX_MEMBERS);
        assertThat(groupMemberRepository.countByGroupIdAndStatus(group.getId(), MemberStatus.ACTIVE)).isEqualTo(1);
        assertThat(groupMemberRepository.findByUserIdAndStatusIn(
            creator.getId(), java.util.EnumSet.of(MemberStatus.ACTIVE))).isNotEmpty();
    }

    @Test
    void persistsDailyGoalWithUniqueDay() {
        User user = userRepository.save(User.ofSocial(AuthProvider.NAVER, "naver-3", null, "서연", 60));
        LocalDate studyDay = LocalDate.of(2026, 6, 27);

        dailyGoalRepository.save(new DailyGoal(user.getId(), studyDay, 120, GoalSource.USER_SET));

        assertThat(dailyGoalRepository.findByUserIdAndStudyDay(user.getId(), studyDay)).isPresent();
    }

    @Test
    void persistsTimerSessionAndClosesIt() {
        User user = userRepository.save(User.ofSocial(AuthProvider.NAVER, "naver-4", null, "도윤", 100));
        LocalDate studyDay = LocalDate.of(2026, 6, 27);
        Instant startedAt = Instant.parse("2026-06-27T05:30:00Z");

        StudySession session = studySessionRepository.save(
            StudySession.openTimer(user.getId(), studyDay, "Spring Boot", startedAt)
        );
        assertThat(studySessionRepository.findByUserIdAndStatus(user.getId(), SessionStatus.OPEN)).isPresent();

        session.close(startedAt.plusSeconds(90 * 60));
        studySessionRepository.saveAndFlush(session);

        StudySession closed = studySessionRepository.findById(session.getId()).orElseThrow();
        assertThat(closed.getStatus()).isEqualTo(SessionStatus.CLOSED);
        assertThat(closed.getDurationMinutes()).isEqualTo(90);
    }

    @Test
    void persistsManualSessionWithComputedDuration() {
        User user = userRepository.save(User.ofSocial(AuthProvider.NAVER, "naver-5", null, "하준", 100));
        LocalDate studyDay = LocalDate.of(2026, 6, 27);
        StudySession manual = StudySession.manual(
            user.getId(),
            studyDay,
            "영어",
            Instant.parse("2026-06-27T00:00:00Z"),
            Instant.parse("2026-06-27T02:30:00Z")
        );

        StudySession saved = studySessionRepository.save(manual);

        assertThat(saved.getDurationMinutes()).isEqualTo(150);
        assertThat(studySessionRepository.findByUserIdAndStudyDay(user.getId(), studyDay)).hasSize(1);
    }
}
