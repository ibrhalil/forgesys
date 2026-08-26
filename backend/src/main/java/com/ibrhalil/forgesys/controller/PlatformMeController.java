package com.ibrhalil.forgesys.controller;

import com.ibrhalil.forgesys.dto.PlatformMeResponse;
import com.ibrhalil.forgesys.entity.PlatformUser;
import com.ibrhalil.forgesys.exception.AuthException;
import com.ibrhalil.forgesys.persistence.repository.PlatformUserRepository;
import com.ibrhalil.forgesys.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** K-50 platform identity self view — platform principals only. */
@RestController
@RequestMapping("/api/v1/platform/me")
@RequiredArgsConstructor
public class PlatformMeController {

    private final PlatformUserRepository platformUserRepository;

    @GetMapping
    @PreAuthorize("authentication.principal.scope == 'platform'")
    public ResponseEntity<PlatformMeResponse> me(@AuthenticationPrincipal CustomUserDetails principal) {
        PlatformUser user = platformUserRepository.findById(principal.getUserId())
                .orElseThrow(AuthException::badCredentials);
        List<String> authorities = principal.getAuthorities().stream()
                .map(Object::toString)
                .toList();
        return ResponseEntity.ok(new PlatformMeResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getUserType().name(),
                authorities));
    }
}
