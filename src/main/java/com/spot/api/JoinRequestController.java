package com.spot.api;

import com.spot.api.dto.GroupDtos.JoinRequestResponse;
import com.spot.auth.AuthenticatedUser;
import com.spot.auth.CurrentUser;
import com.spot.common.ApiResponse;
import com.spot.domain.group.GroupMember;
import com.spot.domain.group.GroupService;
import com.spot.domain.group.GroupService.PendingJoinRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/groups/me/join-requests")
public class JoinRequestController {

    private final GroupService groupService;

    public JoinRequestController(GroupService groupService) {
        this.groupService = groupService;
    }

    @GetMapping
    public ApiResponse<List<JoinRequestResponse>> list(
        @CurrentUser AuthenticatedUser currentUser,
        @RequestParam(required = false) Long groupId
    ) {
        List<JoinRequestResponse> requests;
        if (groupId != null) {
            requests = groupService.listPendingRequests(currentUser.userId(), groupId).stream()
                .map(member -> toResponse(groupId, member))
                .toList();
        } else {
            requests = groupService.listAllPendingRequestsForCreator(currentUser.userId()).stream()
                .map(item -> toResponse(item.groupId(), item.member()))
                .toList();
        }
        return ApiResponse.ok(requests);
    }

    @PostMapping("/{memberId}/approve")
    public ApiResponse<JoinRequestResponse> approve(
        @CurrentUser AuthenticatedUser currentUser,
        @PathVariable Long memberId
    ) {
        GroupMember member = groupService.approve(currentUser.userId(), memberId);
        return ApiResponse.ok(toResponse(member.getGroupId(), member));
    }

    @PostMapping("/{memberId}/reject")
    public ApiResponse<JoinRequestResponse> reject(
        @CurrentUser AuthenticatedUser currentUser,
        @PathVariable Long memberId
    ) {
        GroupMember member = groupService.reject(currentUser.userId(), memberId);
        return ApiResponse.ok(toResponse(member.getGroupId(), member));
    }

    private JoinRequestResponse toResponse(Long groupId, GroupMember member) {
        return new JoinRequestResponse(member.getId(), groupId, member.getUserId(), member.getStatus());
    }
}
