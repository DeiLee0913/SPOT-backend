package com.spot.domain.session;

import com.spot.common.BadRequestException;
import com.spot.common.ConflictException;
import com.spot.common.ForbiddenException;
import com.spot.common.NotFoundException;
import com.spot.common.StudyDayService;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import static com.spot.common.StudyDayService.KST;
import static com.spot.common.StudyDayService.RESET_HOUR;

@Service
public class SessionService {

    private static final int MAX_CATEGORY_LENGTH = 50;
    private static final long MAX_DURATION_MINUTES = 2 * 60;

    private final StudySessionRepository sessionRepository;
    private final StudyDayService studyDayService;

    public SessionService(StudySessionRepository sessionRepository, StudyDayService studyDayService) {
        this.sessionRepository = sessionRepository;
        this.studyDayService = studyDayService;
    }

    @Transactional
    public StudySession start(Long userId, String rawCategory) {
        String category = validateCategory(rawCategory);
        findActive(userId).ifPresent(s -> {
            throw new ConflictException("SESSION_ALREADY_OPEN", "진행 중인 세션이 있습니다.");
        });
        Instant now = studyDayService.now();
        return sessionRepository.save(
            StudySession.openTimer(userId, studyDayService.toStudyDay(now), category, now)
        );
    }

    @Transactional
    public StudySession pause(Long userId, Long sessionId) {
        StudySession session = getOwnedSession(userId, sessionId);
        if (session.getStatus() != SessionStatus.OPEN) {
            throw new ConflictException("SESSION_NOT_RUNNING", "진행 중인 타이머 세션이 아닙니다.");
        }
        if (session.getSource() != SessionSource.TIMER) {
            throw new ConflictException("SESSION_NOT_RUNNING", "타이머 세션만 일시정지할 수 있습니다.");
        }
        session.pause(studyDayService.now());
        return session;
    }

    @Transactional
    public StudySession resume(Long userId, Long sessionId) {
        StudySession session = getOwnedSession(userId, sessionId);
        if (session.getStatus() != SessionStatus.PAUSED) {
            throw new ConflictException("SESSION_NOT_PAUSED", "일시정지된 세션이 아닙니다.");
        }
        session.resume(studyDayService.now());
        return session;
    }

    @Transactional
    public StudySession end(Long userId, Long sessionId) {
        StudySession session = getOwnedSession(userId, sessionId);
        if (session.getStatus() != SessionStatus.OPEN && session.getStatus() != SessionStatus.PAUSED) {
            throw new ConflictException("SESSION_NOT_OPEN", "진행 중인 세션이 아닙니다.");
        }
        session.close(studyDayService.now());
        return session;
    }

    @Transactional
    public StudySession registerManual(Long userId, String rawCategory, Instant startedAt, Instant endedAt) {
        String category = validateCategory(rawCategory);
        validateRange(startedAt, endedAt);
        ensureNoOverlap(userId, startedAt, endedAt);

        LocalDate studyDay = studyDayService.toStudyDay(startedAt);
        return sessionRepository.save(StudySession.manual(userId, studyDay, category, startedAt, endedAt));
    }

    @Transactional
    public void deleteSession(Long userId, Long sessionId) {
        StudySession session = getOwnedSession(userId, sessionId);
        sessionRepository.delete(session);
    }

    @Transactional
    public int closeCrossDaySessions() {
        LocalDate currentStudyDay = studyDayService.currentStudyDay();
        int closed = 0;
        closed += closeStaleSessions(SessionStatus.OPEN, currentStudyDay);
        closed += closeStaleSessions(SessionStatus.PAUSED, currentStudyDay);
        return closed;
    }

    private int closeStaleSessions(SessionStatus status, LocalDate currentStudyDay) {
        List<StudySession> stale = sessionRepository.findByStatusAndStudyDayBefore(status, currentStudyDay);
        for (StudySession session : stale) {
            Instant boundary = session.getStudyDay()
                .plusDays(1)
                .atTime(RESET_HOUR, 0)
                .atZone(KST)
                .toInstant();
            session.close(boundary);
        }
        return stale.size();
    }

    @Transactional(readOnly = true)
    public List<StudySession> today(Long userId) {
        return sessionRepository.findByUserIdAndStudyDay(userId, studyDayService.currentStudyDay());
    }

    /** OPEN 또는 PAUSED 타이머 세션 (진행 중) */
    @Transactional(readOnly = true)
    public Optional<StudySession> findOpen(Long userId) {
        return findActive(userId);
    }

    @Transactional(readOnly = true)
    public Optional<StudySession> findActive(Long userId) {
        return sessionRepository.findByUserIdAndStatusIn(
            userId,
            List.of(SessionStatus.OPEN, SessionStatus.PAUSED)
        );
    }

    private StudySession getOwnedSession(Long userId, Long sessionId) {
        StudySession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new NotFoundException("SESSION_NOT_FOUND", "세션을 찾을 수 없습니다."));
        if (!session.getUserId().equals(userId)) {
            throw new ForbiddenException("NOT_SESSION_OWNER", "본인의 세션만 처리할 수 있습니다.");
        }
        return session;
    }

    private String validateCategory(String rawCategory) {
        String category = rawCategory == null ? "" : rawCategory.trim();
        if (!StringUtils.hasText(category)) {
            throw new BadRequestException("CATEGORY_REQUIRED", "카테고리를 입력해주세요.");
        }
        if (category.length() > MAX_CATEGORY_LENGTH) {
            throw new BadRequestException("CATEGORY_TOO_LONG", "카테고리는 50자 이하여야 합니다.");
        }
        return category;
    }

    private void validateRange(Instant startedAt, Instant endedAt) {
        if (startedAt == null || endedAt == null) {
            throw new BadRequestException("INVALID_TIME_RANGE", "시작/종료 시각을 입력해주세요.");
        }
        if (!endedAt.isAfter(startedAt)) {
            throw new BadRequestException("INVALID_TIME_RANGE", "종료 시각은 시작 시각 이후여야 합니다.");
        }
        Instant now = studyDayService.now();
        if (startedAt.isAfter(now)) {
            throw new BadRequestException("FUTURE_TIME", "시작 시각은 현재 이전이어야 합니다.");
        }
        if (endedAt.isAfter(now)) {
            throw new BadRequestException("FUTURE_TIME", "종료 시각은 현재 이전이어야 합니다.");
        }
        long minutes = Duration.between(startedAt, endedAt).toMinutes();
        if (minutes < 1) {
            throw new BadRequestException("SESSION_TOO_SHORT", "세션은 최소 1분 이상이어야 합니다.");
        }
        if (minutes > MAX_DURATION_MINUTES) {
            throw new BadRequestException("SESSION_TOO_LONG", "세션은 최대 2시간까지 등록할 수 있습니다.");
        }
    }

    private void ensureNoOverlap(Long userId, Instant startedAt, Instant endedAt) {
        boolean overlap = sessionRepository.findByUserId(userId).stream()
            .anyMatch(s -> {
                if (s.getStatus() == SessionStatus.CLOSED) {
                    Instant sStart = s.getStartedAt();
                    Instant sEnd = s.getEndedAt();
                    return sStart.isBefore(endedAt) && startedAt.isBefore(sEnd);
                }
                Instant sStart = s.getStartedAt();
                Instant sEnd = s.activeEndInstant(studyDayService.now());
                return sStart.isBefore(endedAt) && startedAt.isBefore(sEnd);
            });
        if (overlap) {
            throw new ConflictException("SESSION_OVERLAP", "해당 시간에 이미 학습 기록이 있습니다.");
        }
    }
}
