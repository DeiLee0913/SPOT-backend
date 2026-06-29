package com.spot.domain.session;

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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(
    name = "study_session",
    indexes = {
        @Index(name = "idx_study_session_user_status", columnList = "user_id, status"),
        @Index(name = "idx_study_session_user_day", columnList = "user_id, study_day"),
        @Index(name = "idx_study_session_day", columnList = "study_day")
    }
)
public class StudySession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "study_day", nullable = false)
    private LocalDate studyDay;

    @Column(name = "todo_id")
    private Long todoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SessionSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SessionStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "active_duration_seconds", nullable = false)
    private int activeDurationSeconds;

    @Column(name = "last_resumed_at")
    private Instant lastResumedAt;

    @Column(name = "paused_at")
    private Instant pausedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StudySession() {
    }

    private StudySession(
        Long userId,
        LocalDate studyDay,
        Long todoId,
        SessionSource source,
        SessionStatus status,
        Instant startedAt,
        Instant endedAt,
        Integer durationMinutes,
        int activeDurationSeconds,
        Instant lastResumedAt,
        Instant pausedAt
    ) {
        this.userId = userId;
        this.studyDay = studyDay;
        this.todoId = todoId;
        this.source = source;
        this.status = status;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.durationMinutes = durationMinutes;
        this.activeDurationSeconds = activeDurationSeconds;
        this.lastResumedAt = lastResumedAt;
        this.pausedAt = pausedAt;
    }

    public static StudySession openTimer(Long userId, LocalDate studyDay, Long todoId, Instant startedAt) {
        return new StudySession(
            userId,
            studyDay,
            todoId,
            SessionSource.TIMER,
            SessionStatus.OPEN,
            startedAt,
            null,
            null,
            0,
            startedAt,
            null
        );
    }

    public static StudySession manual(
        Long userId,
        LocalDate studyDay,
        Long todoId,
        Instant startedAt,
        Instant endedAt
    ) {
        return new StudySession(
            userId,
            studyDay,
            todoId,
            SessionSource.MANUAL,
            SessionStatus.CLOSED,
            startedAt,
            endedAt,
            toMinutes(startedAt, endedAt),
            0,
            null,
            null
        );
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public void pause(Instant now) {
        if (status != SessionStatus.OPEN) {
            throw new IllegalStateException("OPEN 세션만 일시정지할 수 있습니다.");
        }
        if (lastResumedAt != null) {
            activeDurationSeconds += (int) Duration.between(lastResumedAt, now).getSeconds();
        }
        lastResumedAt = null;
        pausedAt = now;
        status = SessionStatus.PAUSED;
    }

    public void resume(Instant now) {
        if (status != SessionStatus.PAUSED) {
            throw new IllegalStateException("PAUSED 세션만 재개할 수 있습니다.");
        }
        pausedAt = null;
        lastResumedAt = now;
        status = SessionStatus.OPEN;
    }

    public int effectiveDurationSeconds(Instant now) {
        if (status == SessionStatus.CLOSED && durationMinutes != null) {
            return durationMinutes * 60;
        }
        int total = activeDurationSeconds;
        if (status == SessionStatus.OPEN && lastResumedAt != null) {
            total += (int) Duration.between(lastResumedAt, now).getSeconds();
        }
        return Math.max(0, total);
    }

    public void close(Instant endedAt) {
        this.endedAt = endedAt;
        this.durationMinutes = toMinutesFromSeconds(effectiveDurationSeconds(endedAt));
        this.status = SessionStatus.CLOSED;
        this.lastResumedAt = null;
        this.pausedAt = null;
    }

    public void linkTodo(Long todoId) {
        this.todoId = todoId;
    }

    public Instant activeEndInstant(Instant now) {
        return switch (status) {
            case CLOSED -> endedAt;
            case PAUSED -> pausedAt != null ? pausedAt : now;
            case OPEN -> now;
        };
    }

    private static int toMinutes(Instant startedAt, Instant endedAt) {
        return toMinutesFromSeconds((int) Duration.between(startedAt, endedAt).getSeconds());
    }

    private static int toMinutesFromSeconds(int seconds) {
        return seconds / 60;
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

    public Long getTodoId() {
        return todoId;
    }

    public SessionSource getSource() {
        return source;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public int getActiveDurationSeconds() {
        return activeDurationSeconds;
    }

    public Instant getLastResumedAt() {
        return lastResumedAt;
    }

    public Instant getPausedAt() {
        return pausedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
