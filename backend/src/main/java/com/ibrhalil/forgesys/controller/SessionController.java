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
 * Tenant-wide active-session admin view ({@code iam:user:write}) — every active
 * session across all users. Distinct from {@link UserSessionController} (self /
 * per-user); ending a session reuses
 * {@code DELETE /api/v1/users/{id}/sessions/{sessionId}}.
 */
@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;

    @GetMapping
    @PreAuthorize("hasAuthority('iam:user:write')")
    public ResponseEntity<List<AdminSessionResponse>> allSessions() {
        return ResponseEntity.ok(sessionService.listAllSessions());
    }
}
