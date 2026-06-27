package com.spot.domain.group;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(
    name = "group_member",
    indexes = {
        @Index(name = "idx_group_member_group_status", columnList = "group_id, status"),
        @Index(name = "idx_group_member_user_status", columnList = "user_id, status")
    }
)
public class GroupMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberStatus status;

    @Column(name = "joined_at")
    private Instant joinedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected GroupMember() {
    }

    public GroupMember(Long groupId, Long userId, MemberStatus status) {
        this.groupId = groupId;
        this.userId = userId;
        this.status = status;
        if (status == MemberStatus.ACTIVE) {
            this.joinedAt = Instant.now();
        }
    }

    public static GroupMember pending(Long groupId, Long userId) {
        return new GroupMember(groupId, userId, MemberStatus.PENDING);
    }

    public static GroupMember active(Long groupId, Long userId) {
        return new GroupMember(groupId, userId, MemberStatus.ACTIVE);
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public void approve() {
        this.status = MemberStatus.ACTIVE;
        this.joinedAt = Instant.now();
    }

    public void reject() {
        this.status = MemberStatus.REJECTED;
    }

    public void requestAgain() {
        this.status = MemberStatus.PENDING;
    }

    public void leave() {
        this.status = MemberStatus.LEFT;
    }

    public Long getId() {
        return id;
    }

    public Long getGroupId() {
        return groupId;
    }

    public Long getUserId() {
        return userId;
    }

    public MemberStatus getStatus() {
        return status;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
