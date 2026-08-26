package com.ibrhalil.forgesys.config;

import com.ibrhalil.forgesys.security.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * K-50 F6: the AuditorAware must attribute impersonated mutations to the acting
 * platform identity (frozen decision #5), not the target admin whose identity
 * the session borrows.
 */
@SpringBootTest
@ActiveProfiles("test")
class ImpersonationAuditorAwareTest {

    @Autowired
    private org.springframework.data.domain.AuditorAware<String> auditorAware;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void impersonatedSessionAttributesActingPlatformIdentity() {
        UUID targetUserId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        authenticate(new CustomUserDetails(targetUserId, "admin@tenant.test", null,
                true, true, true, true, Set.of(), "public", "jti-1", null,
                actorId.toString(), "Root Admin", true));
        assertThat(auditorAware.getCurrentAuditor()).contains(actorId.toString());
    }

    @Test
    void realLoginAttributesTheTenantUser() {
        UUID targetUserId = UUID.randomUUID();
        authenticate(new CustomUserDetails(targetUserId, "admin@tenant.test", null,
                true, true, true, true, Set.of(), "public", "jti-1", null,
                null, null, false));
        assertThat(auditorAware.getCurrentAuditor()).contains(targetUserId.toString());
    }

    @Test
    void noPrincipalFallsBackToSystem() {
        assertThat(auditorAware.getCurrentAuditor()).contains("system");
    }

    private void authenticate(CustomUserDetails principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
