package com.ibrhalil.forgesys.security;

import com.ibrhalil.forgesys.common.tenant.TenantContext;
import com.ibrhalil.forgesys.persistence.repository.UserRepository;
import com.ibrhalil.forgesys.security.refresh.ActiveSession;
import com.ibrhalil.forgesys.security.refresh.InMemoryRefreshTokenStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Faz 5: the concurrent-session cap. Exercises {@link SessionRevocationService#enforceSessionLimit}
 * against the real test-profile in-memory store (so issue/listSessions/revokeSession run
 * end-to-end). The cap evicts the oldest sessions beyond the limit; login always succeeds
 * and the active-session count stays at/below the cap.
 */
class SessionRevocationServiceTest {

    private static final String TENANT = "tenant_acme";

    private InMemoryRefreshTokenStore store;
    private UUID userId;

    @BeforeEach
    void setUp() {
        store = new InMemoryRefreshTokenStore();
        userId = UUID.randomUUID();
        TenantContext.setCurrentTenant(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void evictsOldestSessionsBeyondTheCap() {
        SessionRevocationService service = new SessionRevocationService(mock(UserRepository.class), store, 2);
        store.issue(userId, "u@acme.com", TENANT, "10.0.0.1", "UA-A");
        store.issue(userId, "u@acme.com", TENANT, "10.0.0.2", "UA-B");
        store.issue(userId, "u@acme.com", TENANT, "10.0.0.3", "UA-C"); // pushes over the cap of 2

        service.enforceSessionLimit(userId);

        List<ActiveSession> remaining = store.listSessions(userId, TENANT);
        assertThat(remaining).hasSize(2);
    }

    @Test
    void keepsAllSessionsWhenUnderTheCap() {
        SessionRevocationService service = new SessionRevocationService(mock(UserRepository.class), store, 5);
        store.issue(userId, "u@acme.com", TENANT, "10.0.0.1", "UA-A");
        store.issue(userId, "u@acme.com", TENANT, "10.0.0.2", "UA-B");

        service.enforceSessionLimit(userId);

        assertThat(store.listSessions(userId, TENANT)).hasSize(2);
    }

    @Test
    void unlimitedCapIsNoOp() {
        SessionRevocationService service = new SessionRevocationService(mock(UserRepository.class), store, 0);
        store.issue(userId, "u@acme.com", TENANT, "10.0.0.1", "UA-A");
        store.issue(userId, "u@acme.com", TENANT, "10.0.0.2", "UA-B");
        store.issue(userId, "u@acme.com", TENANT, "10.0.0.3", "UA-C");

        service.enforceSessionLimit(userId);

        assertThat(store.listSessions(userId, TENANT)).hasSize(3);
    }

    @Test
    void singleSessionSurvivesStrictCap() {
        SessionRevocationService service = new SessionRevocationService(mock(UserRepository.class), store, 1);
        store.issue(userId, "u@acme.com", TENANT, "10.0.0.1", "UA-A");
        store.issue(userId, "u@acme.com", TENANT, "10.0.0.2", "UA-B");

        service.enforceSessionLimit(userId);

        // exactly one session kept (the newest); the other evicted.
        assertThat(store.listSessions(userId, TENANT)).hasSize(1);
    }
}
