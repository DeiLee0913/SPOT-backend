package com.spot.domain.goal;

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
import java.time.LocalDate;

@Entity
@Table(
    name = "daily_goal",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_daily_goal_user_day",
        columnNames = {"user_id", "study_day"}
    )
)
public class DailyGoal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "study_day", nullable = false)
    private LocalDate studyDay;

    @Column(name = "goal_minutes", nullable = false)
    private int goalMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GoalSource source;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DailyGoal() {
    }

    public DailyGoal(Long userId, LocalDate studyDay, int goalMinutes, GoalSource source) {
        this.userId = userId;
        this.studyDay = studyDay;
        this.goalMinutes = goalMinutes;
        this.source = source;
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

    public void changeGoal(int goalMinutes, GoalSource source) {
        this.goalMinutes = goalMinutes;
        this.source = source;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public LocalDate getStudyDay() {
        return studyDay;
    }

    public int getGoalMinutes() {
        return goalMinutes;
    }

    public GoalSource getSource() {
        return source;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
