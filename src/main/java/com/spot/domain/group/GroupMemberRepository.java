package com.spot.domain.group;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {

    Optional<GroupMember> findByGroupIdAndUserId(Long groupId, Long userId);

    List<GroupMember> findByGroupIdAndStatus(Long groupId, MemberStatus status);

    List<GroupMember> findByUserIdAndStatusIn(Long userId, Collection<MemberStatus> statuses);

    long countByGroupIdAndStatus(Long groupId, MemberStatus status);
}
