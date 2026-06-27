package com.spot.domain.group;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "study_group")
public class StudyGroup {

    public static final int DEFAULT_MAX_MEMBERS = 20;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "invite_code", nullable = false, unique = true, length = 8)
    private String inviteCode;

    @Column(name = "creator_user_id", nullable = false)
    private Long creatorUserId;

    @Column(name = "max_members", nullable = false)
    private int maxMembers = DEFAULT_MAX_MEMBERS;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GroupStatus status = GroupStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StudyGroup() {
    }

    public StudyGroup(String name, String inviteCode, Long creatorUserId) {
        this.name = name;
        this.inviteCode = inviteCode;
        this.creatorUserId = creatorUserId;
        this.maxMembers = DEFAULT_MAX_MEMBERS;
        this.status = GroupStatus.ACTIVE;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public void transferCreator(Long newCreatorUserId) {
        this.creatorUserId = newCreatorUserId;
    }

    public void disband() {
        this.status = GroupStatus.DISBANDED;
    }

    public boolean isCreator(Long userId) {
        return this.creatorUserId.equals(userId);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getInviteCode() {
        return inviteCode;
    }

    public Long getCreatorUserId() {
        return creatorUserId;
    }

    public int getMaxMembers() {
        return maxMembers;
    }

    public GroupStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
