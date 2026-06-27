package com.spot.domain.session;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudySessionRepository extends JpaRepository<StudySession, Long> {

    Optional<StudySession> findByUserIdAndStatus(Long userId, SessionStatus status);

    List<StudySession> findByStatusAndStudyDayBefore(SessionStatus status, LocalDate studyDay);

    List<StudySession> findByUserId(Long userId);

    List<StudySession> findByUserIdAndStudyDay(Long userId, LocalDate studyDay);

    List<StudySession> findByUserIdAndStatusAndStudyDay(Long userId, SessionStatus status, LocalDate studyDay);

    List<StudySession> findByUserIdAndStatusAndStudyDayBetween(
        Long userId,
        SessionStatus status,
        LocalDate from,
        LocalDate to
    );
}
