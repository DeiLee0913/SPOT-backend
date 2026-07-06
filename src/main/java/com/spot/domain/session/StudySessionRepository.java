package com.spot.domain.session;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudySessionRepository extends JpaRepository<StudySession, Long> {

    Optional<StudySession> findByUserIdAndStatus(Long userId, SessionStatus status);

    Optional<StudySession> findByUserIdAndStatusIn(Long userId, Collection<SessionStatus> statuses);

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

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update StudySession s
        set s.todoId = null
        where s.userId = :userId and s.todoId = :todoId
        """)
    void clearTodoId(@Param("userId") Long userId, @Param("todoId") Long todoId);
}
