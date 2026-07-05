package com.spot.domain.group;

import com.spot.common.BadRequestException;
import com.spot.common.ConflictException;
import com.spot.common.ForbiddenException;
import com.spot.common.NotFoundException;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class GroupService {

    private static final int MAX_CODE_ATTEMPTS = 10;
    private static final EnumSet<MemberStatus> VISIBLE_MEMBERSHIPS =
        EnumSet.of(MemberStatus.ACTIVE, MemberStatus.PENDING);

    private final StudyGroupRepository groupRepository;
    private final GroupMemberRepository memberRepository;
    private final InviteCodeGenerator inviteCodeGenerator;

    public GroupService(
        StudyGroupRepository groupRepository,
        GroupMemberRepository memberRepository,
        InviteCodeGenerator inviteCodeGenerator
    ) {
        this.groupRepository = groupRepository;
        this.memberRepository = memberRepository;
        this.inviteCodeGenerator = inviteCodeGenerator;
    }

    @Transactional
    public StudyGroup create(Long userId, String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        if (!StringUtils.hasText(name) || name.length() > 50) {
            throw new BadRequestException("INVALID_GROUP_NAME", "그룹 이름은 1~50자여야 합니다.");
        }

        StudyGroup group = groupRepository.save(new StudyGroup(name, generateUniqueCode(), userId));
        memberRepository.save(GroupMember.active(group.getId(), userId));
        return group;
    }

    @Transactional
    public MyGroup join(Long userId, String inviteCode) {
        StudyGroup group = groupRepository.findByInviteCode(inviteCode)
            .filter(g -> g.getStatus() == GroupStatus.ACTIVE)
            .orElseThrow(() -> new NotFoundException("INVITE_CODE_NOT_FOUND", "초대 코드를 확인해주세요."));

        Optional<GroupMember> existing = memberRepository.findByGroupIdAndUserId(group.getId(), userId);
        if (existing.isPresent()) {
            GroupMember member = existing.get();
            switch (member.getStatus()) {
                case ACTIVE -> throw new ConflictException("ALREADY_MEMBER", "이미 그룹에 소속되어 있습니다.");
                case PENDING -> throw new ConflictException("ALREADY_REQUESTED", "이미 승인 대기 중입니다.");
                case REJECTED, LEFT -> {
                    member.requestAgain();
                    return toMyGroup(group, member, group.isCreator(userId));
                }
                default -> throw new IllegalStateException("알 수 없는 멤버 상태");
            }
        }

        GroupMember pending = memberRepository.save(GroupMember.pending(group.getId(), userId));
        return toMyGroup(group, pending, false);
    }

    @Transactional(readOnly = true)
    public List<MyGroup> listMyGroups(Long userId) {
        return memberRepository.findByUserIdAndStatusIn(userId, VISIBLE_MEMBERSHIPS).stream()
            .map(membership -> groupRepository.findById(membership.getGroupId())
                .filter(group -> group.getStatus() == GroupStatus.ACTIVE)
                .map(group -> toMyGroup(group, membership, group.isCreator(userId))))
            .flatMap(Optional::stream)
            .toList();
    }

    @Transactional(readOnly = true)
    public MyGroup getMembership(Long userId, Long groupId) {
        GroupMember membership = memberRepository.findByGroupIdAndUserId(groupId, userId)
            .filter(m -> VISIBLE_MEMBERSHIPS.contains(m.getStatus()))
            .orElseThrow(() -> new NotFoundException("NO_GROUP", "소속된 그룹이 없습니다."));
        StudyGroup group = getGroup(groupId);
        return toMyGroup(group, membership, group.isCreator(userId));
    }

    @Transactional(readOnly = true)
    public Optional<MyGroup> findMembership(Long userId, Long groupId) {
        return memberRepository.findByGroupIdAndUserId(groupId, userId)
            .filter(m -> VISIBLE_MEMBERSHIPS.contains(m.getStatus()))
            .map(membership -> {
                StudyGroup group = getGroup(groupId);
                return toMyGroup(group, membership, group.isCreator(userId));
            });
    }

    @Transactional
    public void leave(Long userId, Long groupId, Long successorUserId) {
        GroupMember me = memberRepository.findByGroupIdAndUserId(groupId, userId)
            .filter(m -> m.getStatus() == MemberStatus.ACTIVE || m.getStatus() == MemberStatus.PENDING)
            .orElseThrow(() -> new NotFoundException("NO_GROUP", "소속된 그룹이 없습니다."));

        if (me.getStatus() == MemberStatus.PENDING) {
            me.leave();
            return;
        }

        StudyGroup group = getGroup(groupId);

        if (group.isCreator(userId)) {
            List<GroupMember> others = memberRepository.findByGroupIdAndStatus(group.getId(), MemberStatus.ACTIVE)
                .stream()
                .filter(m -> !m.getUserId().equals(userId))
                .toList();

            if (others.isEmpty()) {
                group.disband();
                me.leave();
                return;
            }

            if (successorUserId == null) {
                throw new BadRequestException("SUCCESSOR_REQUIRED", "승인 권한을 넘겨받을 멤버를 선택해주세요.");
            }
            boolean validSuccessor = others.stream().anyMatch(m -> m.getUserId().equals(successorUserId));
            if (!validSuccessor) {
                throw new BadRequestException("INVALID_SUCCESSOR", "후계자는 같은 그룹의 활성 멤버여야 합니다.");
            }
            group.transferCreator(successorUserId);
            me.leave();
            return;
        }

        me.leave();
    }

    @Transactional(readOnly = true)
    public List<GroupMember> listPendingRequests(Long creatorUserId, Long groupId) {
        StudyGroup group = creatorGroup(creatorUserId, groupId);
        return memberRepository.findByGroupIdAndStatus(group.getId(), MemberStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public List<PendingJoinRequest> listAllPendingRequestsForCreator(Long creatorUserId) {
        return memberRepository.findByUserIdAndStatusIn(creatorUserId, EnumSet.of(MemberStatus.ACTIVE)).stream()
            .map(m -> getGroup(m.getGroupId()))
            .filter(group -> group.isCreator(creatorUserId) && group.getStatus() == GroupStatus.ACTIVE)
            .flatMap(group -> memberRepository.findByGroupIdAndStatus(group.getId(), MemberStatus.PENDING).stream()
                .map(member -> new PendingJoinRequest(group.getId(), member)))
            .toList();
    }

    @Transactional
    public GroupMember approve(Long creatorUserId, Long memberId, Long groupId) {
        GroupMember member = getMember(memberId);
        assertMemberInGroup(member, groupId);
        creatorGroup(creatorUserId, member.getGroupId());
        if (member.getStatus() != MemberStatus.PENDING) {
            throw new ConflictException("NOT_PENDING", "승인 대기 상태가 아닙니다.");
        }
        StudyGroup group = getGroup(member.getGroupId());
        long activeCount = memberRepository.countByGroupIdAndStatus(group.getId(), MemberStatus.ACTIVE);
        if (activeCount >= group.getMaxMembers()) {
            throw new ConflictException("GROUP_FULL", "그룹 정원이 가득 찼습니다.");
        }
        member.approve();
        return member;
    }

    @Transactional
    public GroupMember reject(Long creatorUserId, Long memberId, Long groupId) {
        GroupMember member = getMember(memberId);
        assertMemberInGroup(member, groupId);
        creatorGroup(creatorUserId, member.getGroupId());
        if (member.getStatus() != MemberStatus.PENDING) {
            throw new ConflictException("NOT_PENDING", "승인 대기 상태가 아닙니다.");
        }
        member.reject();
        return member;
    }

    @Transactional(readOnly = true)
    public Long resolveDashboardGroupId(Long userId, Long groupId) {
        if (groupId != null) {
            return groupId;
        }
        return listMyGroups(userId).stream()
            .filter(g -> g.memberStatus() == MemberStatus.ACTIVE)
            .map(g -> g.group().getId())
            .findFirst()
            .orElseThrow(() -> new NotFoundException("NO_GROUP", "활성 그룹 멤버십이 없습니다."));
    }

    @Transactional(readOnly = true)
    public GroupMember requireActiveMembership(Long userId, Long groupId) {
        GroupMember membership = memberRepository.findByGroupIdAndUserId(groupId, userId)
            .filter(m -> m.getStatus() == MemberStatus.ACTIVE)
            .orElseThrow(() -> new NotFoundException("NO_GROUP", "활성 그룹 멤버십이 없습니다."));
        if (getGroup(groupId).getStatus() != GroupStatus.ACTIVE) {
            throw new NotFoundException("NO_GROUP", "그룹을 찾을 수 없습니다.");
        }
        return membership;
    }

    private StudyGroup creatorGroup(Long creatorUserId, Long groupId) {
        StudyGroup group = getGroup(groupId);
        if (group.getStatus() != GroupStatus.ACTIVE) {
            throw new NotFoundException("GROUP_NOT_FOUND", "그룹을 찾을 수 없습니다.");
        }
        if (!group.isCreator(creatorUserId)) {
            throw new ForbiddenException("NOT_CREATOR", "승인 권한이 없습니다.");
        }
        memberRepository.findByGroupIdAndUserId(groupId, creatorUserId)
            .filter(m -> m.getStatus() == MemberStatus.ACTIVE)
            .orElseThrow(() -> new ForbiddenException("NOT_CREATOR", "승인 권한이 없습니다."));
        return group;
    }

    private GroupMember getMember(Long memberId) {
        return memberRepository.findById(memberId)
            .orElseThrow(() -> new NotFoundException("MEMBER_NOT_FOUND", "가입 요청을 찾을 수 없습니다."));
    }

    private void assertMemberInGroup(GroupMember member, Long groupId) {
        if (groupId != null && !member.getGroupId().equals(groupId)) {
            throw new NotFoundException("MEMBER_NOT_FOUND", "가입 요청을 찾을 수 없습니다.");
        }
    }

    private StudyGroup getGroup(Long groupId) {
        return groupRepository.findById(groupId)
            .orElseThrow(() -> new NotFoundException("GROUP_NOT_FOUND", "그룹을 찾을 수 없습니다."));
    }

    private String generateUniqueCode() {
        for (int i = 0; i < MAX_CODE_ATTEMPTS; i++) {
            String code = inviteCodeGenerator.generate();
            if (!groupRepository.existsByInviteCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("초대 코드 생성에 실패했습니다.");
    }

    private static MyGroup toMyGroup(StudyGroup group, GroupMember membership, boolean creator) {
        return new MyGroup(group, membership.getStatus(), creator);
    }

    public record MyGroup(StudyGroup group, MemberStatus memberStatus, boolean creator) {
    }

    public record PendingJoinRequest(Long groupId, GroupMember member) {
    }
}
