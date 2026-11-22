package com.fitflex.controller;

import com.fitflex.dto.request.PoseSessionRequest;
import com.fitflex.dto.request.ProgressRequest;
import com.fitflex.dto.response.ProgressResponse;
import com.fitflex.dto.response.ProgressSummaryResponse;
import com.fitflex.security.UserPrincipal;
import com.fitflex.service.ProgressService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** Progress tracking for USERs: manual logs, pose script uploads, history and summary. */
@RestController
@RequestMapping("/api/progress")
public class ProgressController {

    private final ProgressService progressService;

    public ProgressController(ProgressService progressService) {
        this.progressService = progressService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProgressResponse log(@AuthenticationPrincipal UserPrincipal me, @Valid @RequestBody ProgressRequest request) {
        return progressService.logManual(me.getId(), request);
    }

    /** GET /api/progress/my?from=2026-11-01&to=2026-11-30 (both optional). */
    @GetMapping("/my")
    public List<ProgressResponse> history(@AuthenticationPrincipal UserPrincipal me,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return progressService.getHistory(me.getId(), from, to);
    }

    @GetMapping("/summary")
    public ProgressSummaryResponse summary(@AuthenticationPrincipal UserPrincipal me) {
        return progressService.getSummary(me.getId());
    }

    /** Called by pose-detection/pose_detector.py with the user's JWT. */
    @PostMapping("/pose")
    @ResponseStatus(HttpStatus.CREATED)
    public ProgressResponse logPose(@AuthenticationPrincipal UserPrincipal me, @Valid @RequestBody PoseSessionRequest request) {
        return progressService.logPoseSession(me.getId(), request);
    }
}
