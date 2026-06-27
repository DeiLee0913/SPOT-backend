package com.spot.api.dto;

import com.spot.domain.goal.GoalService.TodayGoalView;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

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
}
