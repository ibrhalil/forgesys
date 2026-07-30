package com.ibrhalil.forgesys.controller;

import com.ibrhalil.forgesys.dto.AdminSessionResponse;
import com.ibrhalil.forgesys.service.SessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Tenant-wide active-session admin view (the "all sessions" table). Distinct from
 * {@link SessionController} (self / per-user under {@code /api/v1/users/*}) — this lists
 * every active session across all users of the request tenant so an admin can see who is
 * signed in where. Requires {@code iam:user:write} (same as the other admin session
 * endpoints); ending a session reuses {@code DELETE /api/v1/users/{id}/sessions/{sessionId}}.
 */
@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
public class AllSessionsController {

    private final SessionService sessionService;

    @GetMapping
    @PreAuthorize("hasAuthority('iam:user:write')")
    public ResponseEntity<List<AdminSessionResponse>> allSessions() {
        return ResponseEntity.ok(sessionService.listAllSessions());
    }
}
