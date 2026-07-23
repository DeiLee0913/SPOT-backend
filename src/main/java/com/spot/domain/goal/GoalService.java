package com.spot.domain.goal;

import com.spot.common.BadRequestException;
import com.spot.common.ConflictException;
import com.spot.common.NotFoundException;
import com.spot.common.StudyDayService;
import com.spot.domain.session.SessionStatus;
import com.spot.domain.session.StudySession;
import com.spot.domain.session.StudySessionRepository;
import com.spot.domain.user.User;
import com.spot.domain.user.UserRepository;
import com.spot.domain.user.UserStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoalService {

    private final DailyGoalRepository dailyGoalRepository;
    private final UserRepository userRepository;
    private final StudyDayService studyDayService;
    private final StudySessionRepository studySessionRepository;

    public GoalService(
        DailyGoalRepository dailyGoalRepository,
        UserRepository userRepository,
        StudyDayService studyDayService,
        StudySessionRepository studySessionRepository
    ) {
        this.dailyGoalRepository = dailyGoalRepository;
        this.userRepository = userRepository;
        this.studyDayService = studyDayService;
        this.studySessionRepository = studySessionRepository;
    }

    /**
     * 가입 study day에 아직 USER_SET으로 오늘 목표를 정하지 않은 경우 true.
     * 온보딩 라우팅 및 마감(11:00) 이후에도 첫 설정을 허용할 때 사용한다.
     */
    @Transactional(readOnly = true)
    public boolean needsTodayGoalSetup(Long userId) {
        User user = getUser(userId);
        LocalDate today = studyDayService.currentStudyDay(user.getStudyDayResetHour());
        LocalDate joinStudyDay = studyDayService.toStudyDay(user.getCreatedAt(), user.getStudyDayResetHour());
        if (!today.equals(joinStudyDay)) {
            return false;
        }
        Optional<DailyGoal> row = dailyGoalRepository.findByUserIdAndStudyDay(userId, today);
        return row.isEmpty() || row.get().getSource() != GoalSource.USER_SET;
    }

    @Transactional(readOnly = true)
    public TodayGoalView getToday(Long userId) {
        User user = getUser(userId);
        LocalDate today = studyDayService.currentStudyDay(user.getStudyDayResetHour());
        if (needsTodayGoalSetup(userId)) {
            return new TodayGoalView(today, null, null, studyDayService.isAfterGoalDeadline(today));
        }

        Optional<DailyGoal> row = dailyGoalRepository.findByUserIdAndStudyDay(userId, today);
        if (row.isPresent()) {
            DailyGoal goal = row.get();
            return new TodayGoalView(
                today,
                goal.getGoalMinutes(),
                goal.getSource(),
                goal.getSource() == GoalSource.DEFAULT_APPLIED
            );
        }

        boolean afterDeadline = studyDayService.isAfterGoalDeadline(today);
        if (afterDeadline) {
            return new TodayGoalView(today, user.getDefaultGoalMinutes(), GoalSource.DEFAULT_APPLIED, true);
        }
        return new TodayGoalView(today, null, null, false);
    }

    @Transactional
    public DailyGoal setToday(Long userId, int goalMinutes) {
        User user = getUser(userId);
        return setForStudyDay(userId, studyDayService.currentStudyDay(user.getStudyDayResetHour()), goalMinutes);
    }

    /**
     * study day별 목표 저장. 오늘은 마감(11:00) 규칙을 따르고, 미래는 USER_SET으로 저장한다.
     */
    @Transactional
    public DailyGoal setForStudyDay(Long userId, LocalDate studyDay, int goalMinutes) {
        if (goalMinutes < 0) {
            throw new BadRequestException("INVALID_GOAL", "목표 시간은 0분 이상이어야 합니다.");
        }
        User user = getUser(userId);
        LocalDate today = studyDayService.currentStudyDay(user.getStudyDayResetHour());
        if (studyDay.isBefore(today)) {
            throw new BadRequestException("GOAL_PAST_LOCKED", "과거 날짜의 목표는 변경할 수 없습니다.");
        }
        if (studyDay.isEqual(today)) {
            if (studyDayService.isAfterGoalDeadline(today) && !needsTodayGoalSetup(userId)) {
                assertCanChangeGoalAfterDeadline(userId, today, goalMinutes);
            }
            return upsertUserGoal(userId, studyDay, goalMinutes);
        }
        return upsertUserGoal(userId, studyDay, goalMinutes);
    }

    /**
     * 기간별 저장된 일일 목표 + study day별 CLOSED 세션 합({@code actualMinutes}).
     * {@code days}는 from~to 모든 날짜를 포함하며, 목표 행이 없으면 {@code goalMinutes}는 null.
     */
    @Transactional(readOnly = true)
    public GoalRangeView getRange(Long userId, LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new BadRequestException("INVALID_DATE_RANGE", "시작일은 종료일 이전이어야 합니다.");
        }
        if (ChronoUnit.DAYS.between(from, to) > 60) {
            throw new BadRequestException("INVALID_DATE_RANGE", "조회 기간은 최대 60일입니다.");
        }

        List<DailyGoal> storedGoals = dailyGoalRepository.findByUserIdAndStudyDayBetween(userId, from, to);
        Map<LocalDate, DailyGoal> goalByDay = storedGoals.stream()
            .collect(Collectors.toMap(DailyGoal::getStudyDay, Function.identity()));

        Map<LocalDate, Integer> actualByDay = new HashMap<>();
        for (StudySession session : studySessionRepository.findByUserIdAndStatusAndStudyDayBetween(
            userId,
            SessionStatus.CLOSED,
            from,
            to
        )) {
            int minutes = session.getDurationMinutes() == null ? 0 : session.getDurationMinutes();
            actualByDay.merge(session.getStudyDay(), minutes, Integer::sum);
        }

        List<GoalRangeView.StoredGoalDay> goals = storedGoals.stream()
            .map(goal -> new GoalRangeView.StoredGoalDay(
                goal.getStudyDay(),
                goal.getGoalMinutes(),
                goal.getSource(),
                actualByDay.getOrDefault(goal.getStudyDay(), 0)
            ))
            .toList();

        List<GoalRangeView.RangeDay> days = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            DailyGoal goal = goalByDay.get(day);
            days.add(new GoalRangeView.RangeDay(
                day,
                goal == null ? null : goal.getGoalMinutes(),
                actualByDay.getOrDefault(day, 0)
            ));
        }

        return new GoalRangeView(from, to, goals, days);
    }

    private DailyGoal upsertUserGoal(Long userId, LocalDate studyDay, int goalMinutes) {
        return dailyGoalRepository.findByUserIdAndStudyDay(userId, studyDay)
            .map(goal -> {
                goal.changeGoal(goalMinutes, GoalSource.USER_SET);
                return goal;
            })
            .orElseGet(() -> dailyGoalRepository.save(
                new DailyGoal(userId, studyDay, goalMinutes, GoalSource.USER_SET)
            ));
    }

    /**
     * 점수·히스토리·대시보드용 일자별 유효 목표.
     * 명시적으로 설정된 목표가 없더라도 마감(11:00)이 지난 날은 사용자 DEFAULT가 적용된 것으로 본다.
     */
    @Transactional(readOnly = true)
    public Integer effectiveGoalForDay(Long userId, LocalDate day, int defaultGoalMinutes) {
        Optional<DailyGoal> row = dailyGoalRepository.findByUserIdAndStudyDay(userId, day);
        if (row.isPresent()) {
            return row.get().getGoalMinutes();
        }
        User user = getUser(userId);
        LocalDate today = studyDayService.currentStudyDay(user.getStudyDayResetHour());
        if (day.isBefore(today)) {
            return defaultGoalMinutes;
        }
        if (day.isEqual(today) && studyDayService.isAfterGoalDeadline(today)) {
            if (needsTodayGoalSetup(userId)) {
                return null;
            }
            return defaultGoalMinutes;
        }
        return null;
    }

    /**
     * 11:00 마감 스케줄러: 각 사용자 현재 study day에 일일 목표가 없으면 DEFAULT 스냅샷.
     * 계정별 reset hour를 반영하며 재실행해도 안전(idempotent)하다.
     *
     * @return 새로 생성된 일일 목표 수
     */
    @Transactional
    public int applyDefaultGoalsForAllUsers() {
        List<User> activeUsers = userRepository.findByStatus(UserStatus.ACTIVE);
        int created = 0;
        for (User user : activeUsers) {
            LocalDate studyDay = studyDayService.currentStudyDay(user.getStudyDayResetHour());
            if (!studyDayService.isAfterGoalDeadline(studyDay)) {
                continue;
            }
            if (dailyGoalRepository.findByUserIdAndStudyDay(user.getId(), studyDay).isPresent()) {
                continue;
            }
            dailyGoalRepository.save(new DailyGoal(
                user.getId(),
                studyDay,
                user.getDefaultGoalMinutes(),
                GoalSource.DEFAULT_APPLIED
            ));
            created++;
        }
        return created;
    }

    /** @deprecated 테스트 호환 — {@link #applyDefaultGoalsForAllUsers()} 사용 */
    @Transactional
    public int applyDefaultGoals(LocalDate studyDay) {
        Set<Long> alreadySet = dailyGoalRepository.findByStudyDay(studyDay).stream()
            .map(DailyGoal::getUserId)
            .collect(Collectors.toSet());

        List<User> activeUsers = userRepository.findByStatus(UserStatus.ACTIVE);
        int created = 0;
        for (User user : activeUsers) {
            if (alreadySet.contains(user.getId())) {
                continue;
            }
            dailyGoalRepository.save(new DailyGoal(
                user.getId(),
                studyDay,
                user.getDefaultGoalMinutes(),
                GoalSource.DEFAULT_APPLIED
            ));
            created++;
        }
        return created;
    }

    /**
     * 11:00 마감 이후 변경 허용 조건: 오늘 목표를 이미 달성했고, 새 목표가 현재 목표 이상일 때만.
     */
    private void assertCanChangeGoalAfterDeadline(Long userId, LocalDate today, int goalMinutes) {
        int currentGoal = resolveCurrentGoalMinutes(userId, today);
        if (goalMinutes < currentGoal) {
            throw new ConflictException("GOAL_CANNOT_DECREASE", "오전 11시 이후에는 오늘 목표를 낮출 수 없습니다.");
        }
        int studiedMinutes = todayStudyMinutes(userId, today);
        if (studiedMinutes < currentGoal) {
            throw new ConflictException("GOAL_NOT_ACHIEVED", "오늘 목표를 달성한 후에만 목표를 늘릴 수 있습니다.");
        }
    }

    private int resolveCurrentGoalMinutes(Long userId, LocalDate today) {
        return dailyGoalRepository.findByUserIdAndStudyDay(userId, today)
            .map(DailyGoal::getGoalMinutes)
            .orElseGet(() -> getUser(userId).getDefaultGoalMinutes());
    }

    private int todayStudyMinutes(Long userId, LocalDate today) {
        Instant now = studyDayService.now();
        int total = 0;
        for (StudySession session : studySessionRepository.findByUserIdAndStudyDay(userId, today)) {
            if (session.getStatus() == SessionStatus.CLOSED) {
                total += session.getDurationMinutes() != null ? session.getDurationMinutes() : 0;
            } else if (session.getStatus() == SessionStatus.OPEN || session.getStatus() == SessionStatus.PAUSED) {
                total += session.effectiveDurationSeconds(now) / 60;
            }
        }
        return total;
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));
    }

    public record TodayGoalView(LocalDate studyDay, Integer goalMinutes, GoalSource source, boolean deadlineApplied) {
    }

    public record GoalRangeView(
        LocalDate from,
        LocalDate to,
        List<StoredGoalDay> goals,
        List<RangeDay> days
    ) {
        public record StoredGoalDay(
            LocalDate studyDay,
            int goalMinutes,
            GoalSource source,
            int actualMinutes
        ) {
        }

        public record RangeDay(
            LocalDate studyDay,
            Integer goalMinutes,
            int actualMinutes
        ) {
        }
    }
}
