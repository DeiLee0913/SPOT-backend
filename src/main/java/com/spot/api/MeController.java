package com.spot.api;

import com.spot.auth.AuthenticatedUser;
import com.spot.auth.CurrentUser;
import com.spot.common.ApiResponse;
import com.spot.common.BadRequestException;
import com.spot.common.StudyDayService;
import com.spot.domain.group.GroupService;
import com.spot.domain.group.GroupService.MyGroup;
import com.spot.domain.group.MemberStatus;
import com.spot.domain.goal.GoalService;
import com.spot.domain.session.SessionService;
import com.spot.domain.session.StudySession;
import com.spot.domain.user.User;
import com.spot.domain.user.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MeController {

    private final UserService userService;
    private final GroupService groupService;
    private final SessionService sessionService;
    private final GoalService goalService;
    private final StudyDayService studyDayService;

    public MeController(
        UserService userService,
        GroupService groupService,
        SessionService sessionService,
        GoalService goalService,
        StudyDayService studyDayService
    ) {
        this.userService = userService;
        this.groupService = groupService;
        this.sessionService = sessionService;
        this.goalService = goalService;
        this.studyDayService = studyDayService;
    }

    @GetMapping("/me")
    public ApiResponse<MeResponse> me(@CurrentUser AuthenticatedUser currentUser) {
        return ApiResponse.ok(buildMeResponse(currentUser.userId()));
    }

    @PutMapping("/me/default-goal")
    public ApiResponse<MeResponse> updateDefaultGoal(
        @CurrentUser AuthenticatedUser currentUser,
        @Valid @RequestBody UpdateDefaultGoalRequest request
    ) {
        if (request.goalMinutes() < 60) {
            throw new BadRequestException("INVALID_GOAL", "기본 목표 시간은 60분 이상이어야 합니다.");
        }
        userService.updateDefaultGoal(currentUser.userId(), request.goalMinutes());
        return me(currentUser);
    }

    @PutMapping("/me/display-name")
    public ApiResponse<MeResponse> updateDisplayName(
        @CurrentUser AuthenticatedUser currentUser,
        @Valid @RequestBody UpdateDisplayNameRequest request
    ) {
        userService.updateDisplayName(currentUser.userId(), request.displayName());
        return me(currentUser);
    }

    @PutMapping("/me/study-day-reset-hour")
    public ApiResponse<MeResponse> updateStudyDayResetHour(
        @CurrentUser AuthenticatedUser currentUser,
        @Valid @RequestBody UpdateStudyDayResetHourRequest request
    ) {
        userService.updateStudyDayResetHour(currentUser.userId(), request.studyDayResetHour());
        return me(currentUser);
    }

    private MeResponse buildMeResponse(Long userId) {
        User user = userService.getById(userId);
        int resetHour = user.getStudyDayResetHour();
        LocalDate currentStudyDay = studyDayService.currentStudyDay(resetHour);

        List<GroupSummary> groups = groupService.listMyGroups(userId).stream()
            .map(MeController::toGroupSummary)
            .toList();

        GroupSummary group = groups.stream()
            .filter(g -> MemberStatus.ACTIVE.name().equals(g.memberStatus()))
            .findFirst()
            .or(() -> groups.stream().findFirst())
            .orElse(null);

        OpenSession openSession = sessionService.findOpen(userId)
            .map(this::toOpenSession)
            .orElse(null);

        return new MeResponse(
            user.getId(),
            user.resolvedDisplayName(),
            user.getDisplayName(),
            user.getNaverNickname(),
            user.needsDisplayNameSetup(),
            goalService.needsTodayGoalSetup(userId),
            user.getDefaultGoalMinutes(),
            resetHour,
            currentStudyDay,
            groups,
            group,
            openSession
        );
    }

    private static GroupSummary toGroupSummary(MyGroup myGroup) {
        return new GroupSummary(
            myGroup.group().getId(),
            myGroup.group().getName(),
            myGroup.creator() ? "CREATOR" : "MEMBER",
            myGroup.memberStatus().name()
        );
    }

    private OpenSession toOpenSession(StudySession session) {
        return new OpenSession(
            session.getId(),
            session.getTodoId(),
            sessionService.resolveSessionTitle(session),
            session.getStatus().name(),
            session.getStartedAt(),
            session.getActiveDurationSeconds(),
            session.getLastResumedAt()
        );
    }

    public record UpdateDefaultGoalRequest(
        @NotNull(message = "목표 시간을 입력해주세요.")
        @Min(value = 60, message = "기본 목표 시간은 60분 이상이어야 합니다.")
        Integer goalMinutes
    ) {
    }

    public record UpdateDisplayNameRequest(
        @NotBlank(message = "표시 이름을 입력해주세요.") String displayName
    ) {
    }

    public record UpdateStudyDayResetHourRequest(
        @NotNull(message = "일자 전환 시각을 입력해주세요.")
        @Min(value = 0, message = "일자 전환 시각은 0~23시여야 합니다.")
        @Max(value = 23, message = "일자 전환 시각은 0~23시여야 합니다.")
        Integer studyDayResetHour
    ) {
    }

    public record MeResponse(
        Long userId,
        String nickname,
        String displayName,
        String naverNickname,
        boolean needsDisplayNameSetup,
        boolean needsTodayGoalSetup,
        int defaultGoalMinutes,
        int studyDayResetHour,
        LocalDate currentStudyDay,
        List<GroupSummary> groups,
        GroupSummary group,
        OpenSession openSession
    ) {
    }

    public record GroupSummary(Long id, String name, String role, String memberStatus) {
    }

    public record OpenSession(
        Long sessionId,
        Long todoId,
        String title,
        String status,
        Instant startedAt,
        int activeDurationSeconds,
        Instant lastResumedAt
    ) {
    }
}
