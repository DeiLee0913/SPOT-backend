package com.spot.api.dto;

import com.spot.domain.session.StudySession;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;

public final class SessionDtos {

    private SessionDtos() {
    }

    public record StartSessionRequest(
        @NotBlank(message = "카테고리를 입력해주세요.") String category
    ) {
    }

    public record ManualSessionRequest(
        @NotBlank(message = "카테고리를 입력해주세요.") String category,
        @NotNull(message = "시작 시각을 입력해주세요.") Instant startedAt,
        @NotNull(message = "종료 시각을 입력해주세요.") Instant endedAt
    ) {
    }

    public record SessionResponse(
        Long sessionId,
        String category,
        String source,
        String status,
        Instant startedAt,
        Instant endedAt,
        Integer durationMinutes,
        LocalDate studyDay,
        Integer activeDurationSeconds,
        Instant lastResumedAt
    ) {
        public static SessionResponse from(StudySession session) {
            return new SessionResponse(
                session.getId(),
                session.getCategory(),
                session.getSource().name(),
                session.getStatus().name(),
                session.getStartedAt(),
                session.getEndedAt(),
                session.getDurationMinutes(),
                session.getStudyDay(),
                session.getActiveDurationSeconds(),
                session.getLastResumedAt()
            );
        }
    }
}
