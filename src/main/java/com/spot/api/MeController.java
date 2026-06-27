package com.spot.api;

import com.spot.auth.AuthenticatedUser;
import com.spot.auth.CurrentUser;
import com.spot.common.ApiResponse;
import com.spot.common.BadRequestException;
import com.spot.domain.group.GroupService;
import com.spot.domain.group.GroupService.MyGroup;
import com.spot.domain.session.SessionService;
import com.spot.domain.session.StudySession;
import com.spot.domain.user.User;
import com.spot.domain.user.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MeController {

    private final UserService userService;
    private final GroupService groupService;
    private final SessionService sessionService;

    public MeController(UserService userService, GroupService groupService, SessionService sessionService) {
        this.userService = userService;
        this.groupService = groupService;
        this.sessionService = sessionService;
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

    private MeResponse buildMeResponse(Long userId) {
        User user = userService.getById(userId);

        GroupSummary group = groupService.findMyGroup(userId)
            .map(MeController::toGroupSummary)
            .orElse(null);

        OpenSession openSession = sessionService.findOpen(userId)
            .map(MeController::toOpenSession)
            .orElse(null);

        return new MeResponse(
            user.getId(),
            user.resolvedDisplayName(),
            user.getDisplayName(),
            user.getNaverNickname(),
            user.needsDisplayNameSetup(),
            user.getDefaultGoalMinutes(),
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

    private static OpenSession toOpenSession(StudySession session) {
        return new OpenSession(session.getId(), session.getCategory(), session.getStartedAt());
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

    public record MeResponse(
        Long userId,
        String nickname,
        String displayName,
        String naverNickname,
        boolean needsDisplayNameSetup,
        int defaultGoalMinutes,
        GroupSummary group,
        OpenSession openSession
    ) {
    }

    public record GroupSummary(Long id, String name, String role, String memberStatus) {
    }

    public record OpenSession(Long sessionId, String category, Instant startedAt) {
    }
}
