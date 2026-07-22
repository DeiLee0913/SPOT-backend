package com.spot.api.dto;

import com.spot.domain.goal.GoalService.GoalRangeView;
import com.spot.domain.goal.GoalService.TodayGoalView;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

public final class GoalDtos {

    private GoalDtos() {
    }

    public record SetGoalRequest(
        @NotNull(message = "목표 시간을 입력해주세요.")
        @Min(value = 0, message = "목표 시간은 0분 이상이어야 합니다.")
        Integer goalMinutes
    ) {
    }

    public record TodayGoalResponse(
        LocalDate studyDay,
        Integer goalMinutes,
        String source,
        boolean deadlineApplied
    ) {
        public static TodayGoalResponse from(TodayGoalView view) {
            return new TodayGoalResponse(
                view.studyDay(),
                view.goalMinutes(),
                view.source() == null ? null : view.source().name(),
                view.deadlineApplied()
            );
        }
    }

    public record GoalDayResponse(
        LocalDate studyDay,
        int goalMinutes,
        String source,
        int actualMinutes
    ) {
        public static GoalDayResponse from(com.spot.domain.goal.DailyGoal goal) {
            return from(goal, 0);
        }

        public static GoalDayResponse from(com.spot.domain.goal.DailyGoal goal, int actualMinutes) {
            return new GoalDayResponse(
                goal.getStudyDay(),
                goal.getGoalMinutes(),
                goal.getSource().name(),
                actualMinutes
            );
        }

        public static GoalDayResponse from(GoalRangeView.StoredGoalDay day) {
            return new GoalDayResponse(
                day.studyDay(),
                day.goalMinutes(),
                day.source().name(),
                day.actualMinutes()
            );
        }
    }

    public record GoalRangeDayResponse(
        LocalDate studyDay,
        Integer goalMinutes,
        int actualMinutes
    ) {
        public static GoalRangeDayResponse from(GoalRangeView.RangeDay day) {
            return new GoalRangeDayResponse(day.studyDay(), day.goalMinutes(), day.actualMinutes());
        }
    }

    public record GoalRangeResponse(
        LocalDate from,
        LocalDate to,
        List<GoalDayResponse> goals,
        List<GoalRangeDayResponse> days
    ) {
        public static GoalRangeResponse from(GoalRangeView view) {
            return new GoalRangeResponse(
                view.from(),
                view.to(),
                view.goals().stream().map(GoalDayResponse::from).toList(),
                view.days().stream().map(GoalRangeDayResponse::from).toList()
            );
        }
    }
}
