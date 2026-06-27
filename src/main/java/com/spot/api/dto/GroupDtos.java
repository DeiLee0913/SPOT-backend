package com.spot.api.dto;

import com.spot.domain.group.GroupService.MyGroup;
import com.spot.domain.group.MemberStatus;
import com.spot.domain.group.StudyGroup;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class GroupDtos {

    private GroupDtos() {
    }

    public record CreateGroupRequest(@NotBlank(message = "그룹 이름을 입력해주세요.") String name) {
    }

    public record JoinGroupRequest(@NotBlank(message = "초대 코드를 입력해주세요.") String inviteCode) {
    }

    public record LeaveGroupRequest(
        @NotNull(message = "그룹 ID를 입력해주세요.") Long groupId,
        Long successorUserId
    ) {
    }

    public record CreateGroupResponse(Long groupId, String name, String inviteCode, Long creatorUserId) {
        public static CreateGroupResponse from(StudyGroup group) {
            return new CreateGroupResponse(
                group.getId(),
                group.getName(),
                group.getInviteCode(),
                group.getCreatorUserId()
            );
        }
    }

    public record GroupSummaryResponse(
        Long groupId,
        String name,
        String inviteCode,
        Long creatorUserId,
        String role,
        MemberStatus memberStatus
    ) {
        public static GroupSummaryResponse from(MyGroup myGroup) {
            StudyGroup group = myGroup.group();
            return new GroupSummaryResponse(
                group.getId(),
                group.getName(),
                group.getInviteCode(),
                group.getCreatorUserId(),
                myGroup.creator() ? "CREATOR" : "MEMBER",
                myGroup.memberStatus()
            );
        }
    }

    public record JoinRequestResponse(Long memberId, Long groupId, Long userId, MemberStatus status) {
    }
}
