package com.spot.api;

import com.spot.api.dto.GoalDtos.GoalDayResponse;
import com.spot.api.dto.GoalDtos.GoalRangeResponse;
import com.spot.api.dto.GoalDtos.SetGoalRequest;
import com.spot.api.dto.GoalDtos.TodayGoalResponse;
import com.spot.auth.AuthenticatedUser;
import com.spot.auth.CurrentUser;
import com.spot.common.ApiResponse;
import com.spot.domain.goal.DailyGoal;
import com.spot.domain.goal.GoalService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/goals")
public class GoalController {

    private final GoalService goalService;

    public GoalController(GoalService goalService) {
        this.goalService = goalService;
    }

    @GetMapping("/today")
    public ApiResponse<TodayGoalResponse> getToday(@CurrentUser AuthenticatedUser currentUser) {
        return ApiResponse.ok(TodayGoalResponse.from(goalService.getToday(currentUser.userId())));
    }

    @PutMapping("/today")
    public ApiResponse<TodayGoalResponse> setToday(
        @CurrentUser AuthenticatedUser currentUser,
        @Valid @RequestBody SetGoalRequest request
    ) {
        DailyGoal goal = goalService.setToday(currentUser.userId(), request.goalMinutes());
        return ApiResponse.ok(new TodayGoalResponse(
            goal.getStudyDay(),
            goal.getGoalMinutes(),
            goal.getSource().name(),
            false
        ));
    }

    @GetMapping("/range")
    public ApiResponse<GoalRangeResponse> getRange(
        @CurrentUser AuthenticatedUser currentUser,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        var goals = goalService.getRange(currentUser.userId(), from, to).stream()
            .map(GoalDayResponse::from)
            .toList();
        return ApiResponse.ok(new GoalRangeResponse(from, to, goals));
    }

    @PutMapping("/{studyDay}")
    public ApiResponse<GoalDayResponse> setForStudyDay(
        @CurrentUser AuthenticatedUser currentUser,
        @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate studyDay,
        @Valid @RequestBody SetGoalRequest request
    ) {
        DailyGoal goal = goalService.setForStudyDay(currentUser.userId(), studyDay, request.goalMinutes());
        return ApiResponse.ok(GoalDayResponse.from(goal));
    }
}
