package com.spot.api;

import com.spot.api.dto.SessionDtos.ManualSessionRequest;
import com.spot.api.dto.SessionDtos.SessionResponse;
import com.spot.api.dto.SessionDtos.StartSessionRequest;
import com.spot.api.dto.TodoDtos.LinkTodoRequest;
import com.spot.auth.AuthenticatedUser;
import com.spot.auth.CurrentUser;
import com.spot.common.ApiResponse;
import com.spot.common.NotFoundException;
import com.spot.common.StudyDayService;
import com.spot.domain.session.SessionService;
import com.spot.domain.session.StudySession;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/sessions")
public class SessionController {

    private final SessionService sessionService;
    private final StudyDayService studyDayService;

    public SessionController(SessionService sessionService, StudyDayService studyDayService) {
        this.sessionService = sessionService;
        this.studyDayService = studyDayService;
    }

    @PostMapping("/start")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SessionResponse> start(
        @CurrentUser AuthenticatedUser currentUser,
        @Valid @RequestBody(required = false) StartSessionRequest request
    ) {
        Long todoId = request != null ? request.todoId() : null;
        String title = request != null ? request.title() : null;
        StudySession session = sessionService.start(currentUser.userId(), todoId, title);
        return ApiResponse.ok(toResponse(session));
    }

    @PostMapping("/{sessionId}/end")
    public ApiResponse<SessionResponse> end(
        @CurrentUser AuthenticatedUser currentUser,
        @PathVariable Long sessionId
    ) {
        StudySession session = sessionService.end(currentUser.userId(), sessionId);
        return ApiResponse.ok(toResponse(session));
    }

    @PostMapping("/{sessionId}/pause")
    public ApiResponse<SessionResponse> pause(
        @CurrentUser AuthenticatedUser currentUser,
        @PathVariable Long sessionId
    ) {
        StudySession session = sessionService.pause(currentUser.userId(), sessionId);
        return ApiResponse.ok(toResponse(session));
    }

    @PostMapping("/{sessionId}/resume")
    public ApiResponse<SessionResponse> resume(
        @CurrentUser AuthenticatedUser currentUser,
        @PathVariable Long sessionId
    ) {
        StudySession session = sessionService.resume(currentUser.userId(), sessionId);
        return ApiResponse.ok(toResponse(session));
    }

    @PostMapping("/{sessionId}/link-todo")
    public ApiResponse<SessionResponse> linkTodo(
        @CurrentUser AuthenticatedUser currentUser,
        @PathVariable Long sessionId,
        @RequestBody(required = false) LinkTodoRequest request
    ) {
        Long todoId = request != null ? request.todoId() : null;
        StudySession session = sessionService.linkTodo(currentUser.userId(), sessionId, todoId);
        return ApiResponse.ok(toResponse(session));
    }

    @PostMapping("/manual")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SessionResponse> manual(
        @CurrentUser AuthenticatedUser currentUser,
        @Valid @RequestBody ManualSessionRequest request
    ) {
        StudySession session = sessionService.registerManual(
            currentUser.userId(),
            request.title(),
            request.startedAt(),
            request.endedAt()
        );
        return ApiResponse.ok(toResponse(session));
    }

    @DeleteMapping("/{sessionId}")
    public ApiResponse<Void> delete(
        @CurrentUser AuthenticatedUser currentUser,
        @PathVariable Long sessionId
    ) {
        sessionService.deleteSession(currentUser.userId(), sessionId);
        return ApiResponse.ok(null);
    }

    @GetMapping
    public ApiResponse<List<SessionResponse>> list(
        @CurrentUser AuthenticatedUser currentUser,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate studyDay
    ) {
        LocalDate day = studyDay != null ? studyDay : studyDayService.currentStudyDay();
        List<SessionResponse> sessions = sessionService.listForStudyDay(currentUser.userId(), day).stream()
            .map(this::toResponse)
            .toList();
        return ApiResponse.ok(sessions);
    }

    @GetMapping("/today")
    public ApiResponse<List<SessionResponse>> today(@CurrentUser AuthenticatedUser currentUser) {
        return list(currentUser, null);
    }

    @GetMapping("/open")
    public ApiResponse<SessionResponse> open(@CurrentUser AuthenticatedUser currentUser) {
        StudySession session = sessionService.findOpen(currentUser.userId())
            .orElseThrow(() -> new NotFoundException("NO_OPEN_SESSION", "진행 중인 세션이 없습니다."));
        return ApiResponse.ok(toResponse(session));
    }

    private SessionResponse toResponse(StudySession session) {
        return SessionResponse.from(session, sessionService.resolveSessionTitle(session));
    }
}
