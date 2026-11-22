package com.fitflex.controller;

import com.fitflex.dto.request.BookSessionRequest;
import com.fitflex.dto.request.GroupSessionRequest;
import com.fitflex.dto.request.SessionStatusRequest;
import com.fitflex.dto.response.SessionResponse;
import com.fitflex.security.UserPrincipal;
import com.fitflex.service.SessionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Training sessions: booking (USER), group classes (TRAINER creates, USER joins), listing and
 * status changes. Role checks for book/group/join are in SecurityConfig.
 */
@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping("/book")
    @ResponseStatus(HttpStatus.CREATED)
    public SessionResponse book(@AuthenticationPrincipal UserPrincipal me, @Valid @RequestBody BookSessionRequest request) {
        return sessionService.book(me.getId(), request);
    }

    @PostMapping("/group")
    @ResponseStatus(HttpStatus.CREATED)
    public SessionResponse createGroup(@AuthenticationPrincipal UserPrincipal me,
                                       @Valid @RequestBody GroupSessionRequest request) {
        return sessionService.createGroup(me.getId(), request);
    }

    /** Upcoming group sessions that can be joined. */
    @GetMapping("/group")
    public List<SessionResponse> openGroupSessions(@AuthenticationPrincipal UserPrincipal me) {
        return sessionService.getOpenGroupSessions(me.getId());
    }

    @PostMapping("/{id}/join")
    public SessionResponse join(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long id) {
        return sessionService.join(me.getId(), id);
    }

    /** GET /api/sessions/my?filter=upcoming|past (no filter = all). */
    @GetMapping("/my")
    public List<SessionResponse> mySessions(@AuthenticationPrincipal UserPrincipal me,
                                            @RequestParam(required = false) String filter) {
        return sessionService.getMySessions(me.getId(), me.getRole(), filter);
    }

    @PutMapping("/{id}/status")
    public SessionResponse updateStatus(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long id,
                                        @Valid @RequestBody SessionStatusRequest request) {
        return sessionService.updateStatus(me.getId(), me.getRole(), id, request.status());
    }
}
