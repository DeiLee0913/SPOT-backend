package com.spot.domain.session;

import com.spot.common.BadRequestException;
import com.spot.common.ConflictException;
import com.spot.common.ForbiddenException;
import com.spot.common.NotFoundException;
import com.spot.common.StudyDayService;
import com.spot.domain.todo.TodoItem;
import com.spot.domain.todo.TodoService;
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

    private static final long MAX_DURATION_MINUTES = 2 * 60;

    private final StudySessionRepository sessionRepository;
    private final StudyDayService studyDayService;
    private final TodoService todoService;

    public SessionService(
        StudySessionRepository sessionRepository,
        StudyDayService studyDayService,
        TodoService todoService
    ) {
        this.sessionRepository = sessionRepository;
        this.studyDayService = studyDayService;
        this.todoService = todoService;
    }

    @Transactional
    public StudySession start(Long userId, Long todoId, String rawTitle) {
        findActive(userId).ifPresent(s -> {
            throw new ConflictException("SESSION_ALREADY_OPEN", "진행 중인 세션이 있습니다.");
        });
        Long linkedTodoId = resolveTodoForStart(userId, todoId, rawTitle);
        Instant now = studyDayService.now();
        return sessionRepository.save(
            StudySession.openTimer(userId, studyDayService.toStudyDay(now), linkedTodoId, now)
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
    public StudySession linkTodo(Long userId, Long sessionId, Long todoId) {
        StudySession session = getOwnedSession(userId, sessionId);
        if (todoId == null) {
            session.linkTodo(null);
            return session;
        }
        todoService.getOwned(userId, todoId);
        session.linkTodo(todoId);
        return session;
    }

    @Transactional
    public StudySession registerManual(Long userId, String rawTitle, Instant startedAt, Instant endedAt) {
        String title = validateTitle(rawTitle);
        validateRange(startedAt, endedAt);
        ensureNoOverlap(userId, startedAt, endedAt);

        LocalDate studyDay = studyDayService.toStudyDay(startedAt);
        TodoItem todo = todoService.create(userId, title, null, null, null, studyDay);
        return sessionRepository.save(StudySession.manual(userId, studyDay, todo.getId(), startedAt, endedAt));
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

    @Transactional(readOnly = true)
    public String resolveSessionTitle(StudySession session) {
        if (session.getTodoId() == null) {
            return null;
        }
        return todoService.resolveTitle(session.getTodoId());
    }

    private Long resolveTodoForStart(Long userId, Long todoId, String rawTitle) {
        if (todoId != null) {
            todoService.getOwned(userId, todoId);
            return todoId;
        }
        if (StringUtils.hasText(rawTitle)) {
            return todoService.quickCreate(userId, rawTitle).getId();
        }
        return null;
    }

    private StudySession getOwnedSession(Long userId, Long sessionId) {
        StudySession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new NotFoundException("SESSION_NOT_FOUND", "세션을 찾을 수 없습니다."));
        if (!session.getUserId().equals(userId)) {
            throw new ForbiddenException("NOT_SESSION_OWNER", "본인의 세션만 처리할 수 있습니다.");
        }
        return session;
    }

    private String validateTitle(String rawTitle) {
        String title = rawTitle == null ? "" : rawTitle.trim();
        if (!StringUtils.hasText(title)) {
            throw new BadRequestException("TITLE_REQUIRED", "할 일 제목을 입력해주세요.");
        }
        if (title.length() > TodoService.MAX_TITLE_LENGTH) {
            throw new BadRequestException("TITLE_TOO_LONG", "제목은 200자 이하여야 합니다.");
        }
        return title;
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
