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
import org.springframework.util.StringUtils;

@Entity
@Table(
    name = "users",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_users_provider_provider_id",
        columnNames = {"provider", "provider_id"}
    )
)
public class User {

    public static final int MIN_DISPLAY_NAME_LENGTH = 1;
    public static final int MAX_DISPLAY_NAME_LENGTH = 20;

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

    @Column(name = "naver_nickname")
    private String naverNickname;

    @Column(name = "display_name", length = 50)
    private String displayName;

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

    public User(
        AuthProvider provider,
        String providerId,
        String email,
        String naverNickname,
        int defaultGoalMinutes
    ) {
        this.provider = provider;
        this.providerId = providerId;
        this.email = email;
        this.naverNickname = normalizeOptional(naverNickname);
        this.defaultGoalMinutes = defaultGoalMinutes;
        this.status = UserStatus.ACTIVE;
    }

    public static User ofSocial(
        AuthProvider provider,
        String providerId,
        String email,
        String naverNickname,
        int defaultGoalMinutes
    ) {
        return new User(provider, providerId, email, naverNickname, defaultGoalMinutes);
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

    public void updateNaverNickname(String naverNickname) {
        this.naverNickname = normalizeOptional(naverNickname);
    }

    public void changeDisplayName(String displayName) {
        this.displayName = normalizeRequired(displayName);
    }

    /** 그룹·대시보드 등에 노출하는 이름 */
    public String resolvedDisplayName() {
        if (StringUtils.hasText(displayName)) {
            return displayName.trim();
        }
        if (StringUtils.hasText(naverNickname)) {
            return naverNickname.trim();
        }
        return "사용자";
    }

    public boolean needsDisplayNameSetup() {
        return !StringUtils.hasText(displayName);
    }

    public void softDelete() {
        this.status = UserStatus.DELETED;
        this.providerId = "deleted-" + this.id;
        this.email = null;
        this.naverNickname = null;
        this.displayName = "탈퇴한 사용자";
        this.deletedAt = Instant.now();
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String normalizeRequired(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("display name required");
        }
        return value.trim();
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

    public String getNaverNickname() {
        return naverNickname;
    }

    public String getDisplayName() {
        return displayName;
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
