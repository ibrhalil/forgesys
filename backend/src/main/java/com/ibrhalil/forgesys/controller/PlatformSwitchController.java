package com.ibrhalil.forgesys.controller;

import com.ibrhalil.forgesys.dto.PlatformSwitchStartRequest;
import com.ibrhalil.forgesys.dto.PlatformSwitchStartResponse;
import com.ibrhalil.forgesys.service.PlatformSwitchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * K-50 F6: tenant switch start (platform console). Deliberately its own controller —
 * the exchange lives on the tenant surface at {@code POST /api/v1/auth/platform-switch}
 * (AuthController) because it runs on the target tenant's subdomain host.
 */
@RestController
@RequestMapping("/api/v1/platform/companies")
@RequiredArgsConstructor
public class PlatformSwitchController {

    /** Platform tokens only (F3 pattern) + the switch-specific authority. */
    private static final String PLATFORM_SWITCH =
            "hasAuthority('platform:tenant:access') and authentication.principal.scope == 'platform'";

    private final PlatformSwitchService platformSwitchService;

    /** Issues a one-time switch code (30s TTL) + the tenant tab URL; reason is mandatory. */
    @PostMapping("/{id}/switch")
    @PreAuthorize(PLATFORM_SWITCH)
    public PlatformSwitchStartResponse start(@PathVariable UUID id,
                                             @Valid @RequestBody PlatformSwitchStartRequest request) {
        return platformSwitchService.start(id, request.reason());
    }
}
