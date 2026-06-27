package com.spot.domain.dashboard;

import com.spot.api.dto.DashboardDtos.DashboardResponse;
import com.spot.api.dto.DashboardDtos.DashboardSession;
import com.spot.api.dto.DashboardDtos.HistoryDay;
import com.spot.api.dto.DashboardDtos.MemberDashboard;
import com.spot.api.dto.DashboardDtos.ScoreBreakdown;
import com.spot.api.dto.DashboardDtos.ScoreRange;
import com.spot.common.StudyDayService;
import com.spot.domain.goal.DailyGoal;
import com.spot.domain.goal.DailyGoalRepository;
import com.spot.domain.group.GroupMember;
import com.spot.domain.group.GroupMemberRepository;
import com.spot.domain.group.GroupService;
import com.spot.domain.group.MemberStatus;
import com.spot.domain.session.SessionStatus;
import com.spot.domain.session.StudySession;
import com.spot.domain.session.StudySessionRepository;
import com.spot.domain.user.User;
import com.spot.domain.user.UserRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private static final int HISTORY_DAYS = 7;
    private static final int GOAL_ACHIEVED_POINTS_PER_DAY = 3;
    private static final int STUDY_POINTS_PER_HOUR = 1;

    private final GroupMemberRepository memberRepository;
    private final GroupService groupService;
    private final UserRepository userRepository;
    private final DailyGoalRepository dailyGoalRepository;
    private final StudySessionRepository sessionRepository;
    private final StudyDayService studyDayService;

    public DashboardService(
        GroupMemberRepository memberRepository,
        GroupService groupService,
        UserRepository userRepository,
        DailyGoalRepository dailyGoalRepository,
        StudySessionRepository sessionRepository,
        StudyDayService studyDayService
    ) {
        this.memberRepository = memberRepository;
        this.groupService = groupService;
        this.userRepository = userRepository;
        this.dailyGoalRepository = dailyGoalRepository;
        this.sessionRepository = sessionRepository;
        this.studyDayService = studyDayService;
    }

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(Long userId, Long groupId) {
        groupService.requireActiveMembership(userId, groupId);

        LocalDate today = studyDayService.currentStudyDay();
        LocalDate historyStart = today.minusDays(HISTORY_DAYS - 1L);
        LocalDate weekStart = studyDayService.weekMonday(today);
        boolean afterDeadlineToday = studyDayService.isAfterGoalDeadline(today);

        List<GroupMember> activeMembers = memberRepository.findByGroupIdAndStatus(
            groupId, MemberStatus.ACTIVE);

        List<MemberAccumulator> accumulators = new ArrayList<>();
        for (GroupMember member : activeMembers) {
            User user = userRepository.findById(member.getUserId()).orElse(null);
            if (user == null) {
                continue;
            }
            accumulators.add(buildAccumulator(
                member, user, today, historyStart, weekStart, afterDeadlineToday));
        }

        assignRanks(accumulators);

        List<MemberDashboard> members = accumulators.stream()
            .map(acc -> acc.toDashboard(weekStart, today))
            .toList();
        return new DashboardResponse(today, members);
    }

    private MemberAccumulator buildAccumulator(
        GroupMember membership,
        User user,
        LocalDate today,
        LocalDate historyStart,
        LocalDate weekStart,
        boolean afterDeadlineToday
    ) {
        LocalDate joinedStudyDay = resolveJoinedStudyDay(membership, today);
        int defaultGoal = user.getDefaultGoalMinutes();

        Map<LocalDate, Integer> minutesByDay = new HashMap<>();
        for (StudySession session : sessionRepository.findByUserIdAndStatusAndStudyDayBetween(
            user.getId(), SessionStatus.CLOSED, historyStart, today)) {
            minutesByDay.merge(
                session.getStudyDay(),
                session.getDurationMinutes() == null ? 0 : session.getDurationMinutes(),
                Integer::sum
            );
        }

        Map<LocalDate, Integer> goalByDay = new HashMap<>();
        for (DailyGoal goal : dailyGoalRepository.findByUserIdAndStudyDayBetween(
            user.getId(), historyStart, today)) {
            goalByDay.put(goal.getStudyDay(), goal.getGoalMinutes());
        }

        int todayMinutes = minutesByDay.getOrDefault(today, 0);
        Integer todayGoal = effectiveGoal(today, goalByDay, today, afterDeadlineToday, defaultGoal);

        List<HistoryDay> history = new ArrayList<>();
        for (int i = 0; i < HISTORY_DAYS; i++) {
            LocalDate day = historyStart.plusDays(i);
            if (day.isBefore(joinedStudyDay)) {
                history.add(new HistoryDay(day, 0, null));
                continue;
            }
            history.add(new HistoryDay(
                day,
                minutesByDay.getOrDefault(day, 0),
                effectiveGoal(day, goalByDay, today, afterDeadlineToday, defaultGoal)
            ));
        }

        long achievementPoints = 0;
        long volumeBonus = 0;
        for (LocalDate day = weekStart; !day.isAfter(today); day = day.plusDays(1)) {
            if (day.isBefore(joinedStudyDay)) {
                continue;
            }
            int actual = minutesByDay.getOrDefault(day, 0);
            Integer goal = effectiveGoal(day, goalByDay, today, afterDeadlineToday, defaultGoal);
            if (goal != null && actual >= goal) {
                achievementPoints += GOAL_ACHIEVED_POINTS_PER_DAY;
            }
            volumeBonus += actual / 60 * STUDY_POINTS_PER_HOUR;
        }

        List<DashboardSession> sessions = sessionRepository
            .findByUserIdAndStatusAndStudyDay(user.getId(), SessionStatus.CLOSED, today).stream()
            .map(s -> new DashboardSession(
                s.getId(), s.getCategory(), s.getStartedAt(), s.getEndedAt(), s.getDurationMinutes()))
            .toList();

        double rawRate = (todayGoal != null && todayGoal > 0) ? todayMinutes * 100.0 / todayGoal : 0.0;

        MemberAccumulator acc = new MemberAccumulator();
        acc.userId = user.getId();
        acc.nickname = user.resolvedDisplayName();
        acc.todayMinutes = todayMinutes;
        acc.todayGoal = todayGoal;
        acc.rawAchievementRate = round1(rawRate);
        acc.displayAchievementRate = round1(Math.min(100.0, rawRate));
        acc.achievementPoints = achievementPoints;
        acc.volumeBonus = volumeBonus;
        acc.weeklyScore = achievementPoints + volumeBonus;
        acc.history = history;
        acc.sessions = sessions;
        return acc;
    }

    private LocalDate resolveJoinedStudyDay(GroupMember membership, LocalDate today) {
        if (membership.getJoinedAt() != null) {
            return studyDayService.toStudyDay(membership.getJoinedAt());
        }
        if (membership.getCreatedAt() != null) {
            return studyDayService.toStudyDay(membership.getCreatedAt());
        }
        return today;
    }

    private Integer effectiveGoal(
        LocalDate day,
        Map<LocalDate, Integer> goalByDay,
        LocalDate today,
        boolean afterDeadlineToday,
        int defaultGoal
    ) {
        Integer explicit = goalByDay.get(day);
        if (explicit != null) {
            return explicit;
        }
        if (day.isBefore(today)) {
            return defaultGoal;
        }
        if (day.isEqual(today) && afterDeadlineToday) {
            return defaultGoal;
        }
        return null;
    }

    private void assignRanks(List<MemberAccumulator> accumulators) {
        for (MemberAccumulator member : accumulators) {
            long higher = accumulators.stream()
                .filter(other -> other.weeklyScore > member.weeklyScore)
                .count();
            member.weeklyRank = (int) (higher + 1);
        }
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static final class MemberAccumulator {
        private Long userId;
        private String nickname;
        private int todayMinutes;
        private Integer todayGoal;
        private double rawAchievementRate;
        private double displayAchievementRate;
        private long achievementPoints;
        private long volumeBonus;
        private long weeklyScore;
        private int weeklyRank;
        private List<HistoryDay> history;
        private List<DashboardSession> sessions;

        private MemberDashboard toDashboard(LocalDate weekStart, LocalDate today) {
            return new MemberDashboard(
                userId,
                nickname,
                todayMinutes,
                todayGoal,
                rawAchievementRate,
                displayAchievementRate,
                weeklyScore,
                weeklyRank,
                new ScoreBreakdown(achievementPoints, volumeBonus),
                new ScoreRange(weekStart, today),
                history,
                sessions
            );
        }
    }
}
