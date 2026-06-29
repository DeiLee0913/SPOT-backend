package com.spot.api;

import com.spot.api.dto.SessionDtos.ManualSessionRequest;
import com.spot.api.dto.SessionDtos.SessionResponse;
import com.spot.api.dto.SessionDtos.StartSessionRequest;
import com.spot.auth.AuthenticatedUser;
import com.spot.auth.CurrentUser;
import com.spot.common.ApiResponse;
import com.spot.common.NotFoundException;
import com.spot.domain.session.SessionService;
import com.spot.domain.session.StudySession;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/sessions")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping("/start")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SessionResponse> start(
        @CurrentUser AuthenticatedUser currentUser,
        @Valid @RequestBody StartSessionRequest request
    ) {
        StudySession session = sessionService.start(currentUser.userId(), request.category());
        return ApiResponse.ok(SessionResponse.from(session));
    }

    @PostMapping("/{sessionId}/end")
    public ApiResponse<SessionResponse> end(
        @CurrentUser AuthenticatedUser currentUser,
        @PathVariable Long sessionId
    ) {
        StudySession session = sessionService.end(currentUser.userId(), sessionId);
        return ApiResponse.ok(SessionResponse.from(session));
    }

    @PostMapping("/{sessionId}/pause")
    public ApiResponse<SessionResponse> pause(
        @CurrentUser AuthenticatedUser currentUser,
        @PathVariable Long sessionId
    ) {
        StudySession session = sessionService.pause(currentUser.userId(), sessionId);
        return ApiResponse.ok(SessionResponse.from(session));
    }

    @PostMapping("/{sessionId}/resume")
    public ApiResponse<SessionResponse> resume(
        @CurrentUser AuthenticatedUser currentUser,
        @PathVariable Long sessionId
    ) {
        StudySession session = sessionService.resume(currentUser.userId(), sessionId);
        return ApiResponse.ok(SessionResponse.from(session));
    }

    @PostMapping("/manual")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SessionResponse> manual(
        @CurrentUser AuthenticatedUser currentUser,
        @Valid @RequestBody ManualSessionRequest request
    ) {
        StudySession session = sessionService.registerManual(
            currentUser.userId(),
            request.category(),
            request.startedAt(),
            request.endedAt()
        );
        return ApiResponse.ok(SessionResponse.from(session));
    }

    @DeleteMapping("/{sessionId}")
    public ApiResponse<Void> delete(
        @CurrentUser AuthenticatedUser currentUser,
        @PathVariable Long sessionId
    ) {
        sessionService.deleteSession(currentUser.userId(), sessionId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/today")
    public ApiResponse<List<SessionResponse>> today(@CurrentUser AuthenticatedUser currentUser) {
        List<SessionResponse> sessions = sessionService.today(currentUser.userId()).stream()
            .map(SessionResponse::from)
            .toList();
        return ApiResponse.ok(sessions);
    }

    @GetMapping("/open")
    public ApiResponse<SessionResponse> open(@CurrentUser AuthenticatedUser currentUser) {
        StudySession session = sessionService.findOpen(currentUser.userId())
            .orElseThrow(() -> new NotFoundException("NO_OPEN_SESSION", "진행 중인 세션이 없습니다."));
        return ApiResponse.ok(SessionResponse.from(session));
    }
}
