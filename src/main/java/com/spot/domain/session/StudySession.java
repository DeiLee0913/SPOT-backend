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

    @Column(nullable = false, length = 50)
    private String category;

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

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StudySession() {
    }

    private StudySession(
        Long userId,
        LocalDate studyDay,
        String category,
        SessionSource source,
        SessionStatus status,
        Instant startedAt,
        Instant endedAt,
        Integer durationMinutes
    ) {
        this.userId = userId;
        this.studyDay = studyDay;
        this.category = category;
        this.source = source;
        this.status = status;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.durationMinutes = durationMinutes;
    }

    public static StudySession openTimer(Long userId, LocalDate studyDay, String category, Instant startedAt) {
        return new StudySession(userId, studyDay, category, SessionSource.TIMER, SessionStatus.OPEN, startedAt, null, null);
    }

    public static StudySession manual(
        Long userId,
        LocalDate studyDay,
        String category,
        Instant startedAt,
        Instant endedAt
    ) {
        return new StudySession(
            userId,
            studyDay,
            category,
            SessionSource.MANUAL,
            SessionStatus.CLOSED,
            startedAt,
            endedAt,
            toMinutes(startedAt, endedAt)
        );
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public void close(Instant endedAt) {
        this.endedAt = endedAt;
        this.durationMinutes = toMinutes(this.startedAt, endedAt);
        this.status = SessionStatus.CLOSED;
    }

    public void updateManual(LocalDate studyDay, String category, Instant startedAt, Instant endedAt) {
        this.studyDay = studyDay;
        this.category = category;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.durationMinutes = toMinutes(startedAt, endedAt);
    }

    public boolean isManual() {
        return this.source == SessionSource.MANUAL;
    }

    private static int toMinutes(Instant startedAt, Instant endedAt) {
        return (int) Duration.between(startedAt, endedAt).toMinutes();
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

    public String getCategory() {
        return category;
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

    public Instant getCreatedAt() {
        return createdAt;
    }
}
