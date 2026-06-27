package com.spot.domain.goal;

import com.spot.common.BadRequestException;
import com.spot.common.ConflictException;
import com.spot.common.StudyDayService;
import com.spot.domain.user.User;
import com.spot.domain.user.UserRepository;
import com.spot.domain.user.UserStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoalService {

    private final DailyGoalRepository dailyGoalRepository;
    private final UserRepository userRepository;
    private final StudyDayService studyDayService;

    public GoalService(
        DailyGoalRepository dailyGoalRepository,
        UserRepository userRepository,
        StudyDayService studyDayService
    ) {
        this.dailyGoalRepository = dailyGoalRepository;
        this.userRepository = userRepository;
        this.studyDayService = studyDayService;
    }

    @Transactional(readOnly = true)
    public TodayGoalView getToday(Long userId) {
        LocalDate today = studyDayService.currentStudyDay();
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
            int defaultGoal = getUser(userId).getDefaultGoalMinutes();
            return new TodayGoalView(today, defaultGoal, GoalSource.DEFAULT_APPLIED, true);
        }
        return new TodayGoalView(today, null, null, false);
    }

    @Transactional
    public DailyGoal setToday(Long userId, int goalMinutes) {
        if (goalMinutes < 0) {
            throw new BadRequestException("INVALID_GOAL", "목표 시간은 0분 이상이어야 합니다.");
        }
        LocalDate today = studyDayService.currentStudyDay();
        if (studyDayService.isAfterGoalDeadline(today)) {
            throw new ConflictException("GOAL_DEADLINE_PASSED", "오전 10시 이후에는 오늘 목표를 변경할 수 없습니다.");
        }

        return dailyGoalRepository.findByUserIdAndStudyDay(userId, today)
            .map(goal -> {
                goal.changeGoal(goalMinutes, GoalSource.USER_SET);
                return goal;
            })
            .orElseGet(() -> dailyGoalRepository.save(
                new DailyGoal(userId, today, goalMinutes, GoalSource.USER_SET)
            ));
    }

    /**
     * 점수·히스토리·대시보드용 일자별 유효 목표.
     * 명시적으로 설정된 목표가 없더라도 마감(10:00)이 지난 날은 사용자 DEFAULT가 적용된 것으로 본다.
     */
    @Transactional(readOnly = true)
    public Integer effectiveGoalForDay(Long userId, LocalDate day, int defaultGoalMinutes) {
        Optional<DailyGoal> row = dailyGoalRepository.findByUserIdAndStudyDay(userId, day);
        if (row.isPresent()) {
            return row.get().getGoalMinutes();
        }
        LocalDate today = studyDayService.currentStudyDay();
        if (day.isBefore(today)) {
            return defaultGoalMinutes;
        }
        if (day.isEqual(today) && studyDayService.isAfterGoalDeadline(today)) {
            return defaultGoalMinutes;
        }
        return null;
    }

    /**
     * 10:00 마감 스케줄러: 해당 study day에 일일 목표가 없는 ACTIVE 사용자에게
     * 본인 DEFAULT 목표를 스냅샷(DEFAULT_APPLIED)으로 확정한다. 재실행해도 안전(idempotent)하다.
     *
     * @return 새로 생성된 일일 목표 수
     */
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

    private User getUser(Long userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new IllegalStateException("사용자를 찾을 수 없습니다: " + userId));
    }

    public record TodayGoalView(LocalDate studyDay, Integer goalMinutes, GoalSource source, boolean deadlineApplied) {
    }
}
