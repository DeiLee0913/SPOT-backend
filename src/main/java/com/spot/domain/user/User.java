package com.spot.domain.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(
    name = "users",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_users_provider_provider_id",
        columnNames = {"provider", "provider_id"}
    )
)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthProvider provider;

    @Column(name = "provider_id", nullable = false)
    private String providerId;

    @Column
    private String email;

    @Column(nullable = false)
    private String nickname;

    @Column(name = "default_goal_minutes", nullable = false)
    private int defaultGoalMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected User() {
    }

    public User(AuthProvider provider, String providerId, String email, String nickname, int defaultGoalMinutes) {
        this.provider = provider;
        this.providerId = providerId;
        this.email = email;
        this.nickname = nickname;
        this.defaultGoalMinutes = defaultGoalMinutes;
        this.status = UserStatus.ACTIVE;
    }

    public static User ofSocial(
        AuthProvider provider,
        String providerId,
        String email,
        String nickname,
        int defaultGoalMinutes
    ) {
        return new User(provider, providerId, email, nickname, defaultGoalMinutes);
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public void changeDefaultGoalMinutes(int defaultGoalMinutes) {
        this.defaultGoalMinutes = defaultGoalMinutes;
    }

    public void changeNickname(String nickname) {
        this.nickname = nickname;
    }

    public void softDelete() {
        this.status = UserStatus.DELETED;
        this.providerId = "deleted-" + this.id;
        this.email = null;
        this.nickname = "탈퇴한 사용자";
        this.deletedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public AuthProvider getProvider() {
        return provider;
    }

    public String getProviderId() {
        return providerId;
    }

    public String getEmail() {
        return email;
    }

    public String getNickname() {
        return nickname;
    }

    public int getDefaultGoalMinutes() {
        return defaultGoalMinutes;
    }

    public UserStatus getStatus() {
        return status;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
