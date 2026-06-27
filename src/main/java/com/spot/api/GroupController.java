package com.spot.api;

import com.spot.api.dto.GroupDtos.CreateGroupRequest;
import com.spot.api.dto.GroupDtos.CreateGroupResponse;
import com.spot.api.dto.GroupDtos.GroupSummaryResponse;
import com.spot.api.dto.GroupDtos.JoinGroupRequest;
import com.spot.api.dto.GroupDtos.LeaveGroupRequest;
import com.spot.auth.AuthenticatedUser;
import com.spot.auth.CurrentUser;
import com.spot.common.ApiResponse;
import com.spot.domain.group.GroupService;
import com.spot.domain.group.StudyGroup;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/groups")
public class GroupController {

    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CreateGroupResponse> create(
        @CurrentUser AuthenticatedUser currentUser,
        @Valid @RequestBody CreateGroupRequest request
    ) {
        StudyGroup group = groupService.create(currentUser.userId(), request.name());
        return ApiResponse.ok(CreateGroupResponse.from(group));
    }

    @PostMapping("/join")
    public ApiResponse<GroupSummaryResponse> join(
        @CurrentUser AuthenticatedUser currentUser,
        @Valid @RequestBody JoinGroupRequest request
    ) {
        groupService.join(currentUser.userId(), request.inviteCode());
        return ApiResponse.ok(GroupSummaryResponse.from(groupService.getMyGroup(currentUser.userId())));
    }

    @GetMapping("/me")
    public ApiResponse<GroupSummaryResponse> myGroup(@CurrentUser AuthenticatedUser currentUser) {
        return ApiResponse.ok(GroupSummaryResponse.from(groupService.getMyGroup(currentUser.userId())));
    }

    @PostMapping("/me/leave")
    public ApiResponse<Void> leave(
        @CurrentUser AuthenticatedUser currentUser,
        @RequestBody(required = false) LeaveGroupRequest request
    ) {
        Long successorUserId = request == null ? null : request.successorUserId();
        groupService.leave(currentUser.userId(), successorUserId);
        return ApiResponse.ok(null);
    }
}
