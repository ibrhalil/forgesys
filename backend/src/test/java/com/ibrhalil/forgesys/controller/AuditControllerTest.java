package com.ibrhalil.forgesys.controller;

import com.ibrhalil.forgesys.entity.AuditLog;
import com.ibrhalil.forgesys.entity.LoginHistory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuditControllerTest extends AbstractRbacWebTest {

    private static final MediaType JSON = MediaType.APPLICATION_JSON;

    @Test
    void auditLogsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/audit-logs"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth_unauthenticated"));
    }

    @Test
    void auditLogsForbiddenWithoutPermission() throws Exception {
        mockMvc.perform(get("/api/v1/audit-logs").cookie(auth("nop@tenant.test")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth_access_denied"));
    }

    @Test
    void auditLogsReturnSeededEntries() throws Exception {
        AuditLog entry = new AuditLog();
        entry.setAction("user_created");
        entry.setActorName("audit_seeded_actor@example.com");
        entry.setEntityType("User");
        entityManager.persist(entry);

        mockMvc.perform(get("/api/v1/audit-logs").cookie(auth("reader@tenant.test", "iam:audit:read")))
                .andExpect(status().isOk())
                // Membership (not position): other @SpringBootTest classes share this H2 and their
                // REQUIRES_NEW audit writes survive @Transactional rollback, so the seeded row may
                // not be at content[0].
                .andExpect(jsonPath("$.data[*].actorName").value(hasItem("audit_seeded_actor@example.com")));
    }

    @Test
    void auditLogsFilterByAction() throws Exception {
        AuditLog probe = new AuditLog();
        probe.setAction("test_probe_audit_filter");
        probe.setActorName("filter_probe@example.com");
        probe.setEntityType("User");
        entityManager.persist(probe);

        // A unique sentinel action is matched only by this probe (no real action collides),
        // so the filtered page has exactly one element regardless of cross-test pollution.
        mockMvc.perform(get("/api/v1/audit-logs")
                        .param("action", "test_probe_audit_filter")
                        .cookie(auth("reader@tenant.test", "iam:audit:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.totalElements").value(1))
                .andExpect(jsonPath("$.data[0].action").value("test_probe_audit_filter"));
    }

    @Test
    void auditLogsCombineActionAndActorFilters() throws Exception {
        // First-match dispatch is gone: both filters must apply (AND). Two probes share
        // the action but differ in actor — only the matching pair survives.
        java.util.UUID actorA = java.util.UUID.randomUUID();
        java.util.UUID actorB = java.util.UUID.randomUUID();
        AuditLog probeA = new AuditLog();
        probeA.setAction("test_probe_combined_filter");
        probeA.setActorId(actorA);
        probeA.setActorName("combined_a@example.com");
        probeA.setEntityType("User");
        entityManager.persist(probeA);
        AuditLog probeB = new AuditLog();
        probeB.setAction("test_probe_combined_filter");
        probeB.setActorId(actorB);
        probeB.setActorName("combined_b@example.com");
        probeB.setEntityType("User");
        entityManager.persist(probeB);

        mockMvc.perform(get("/api/v1/audit-logs")
                        .param("action", "test_probe_combined_filter")
                        .param("actorId", actorA.toString())
                        .cookie(auth("reader@tenant.test", "iam:audit:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.totalElements").value(1))
                .andExpect(jsonPath("$.data[0].actorName").value("combined_a@example.com"));
    }

    @Test
    void auditLogsRejectSortOutsideWhitelist() throws Exception {
        mockMvc.perform(get("/api/v1/audit-logs")
                        .param("sort", "requestBody")
                        .cookie(auth("reader@tenant.test", "iam:audit:read")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"));
    }

    @Test
    void auditLogsSearchByQMatchesAction() throws Exception {
        // Unique sentinels: other @SpringBootTest classes' REQUIRES_NEW audit writes survive
        // rollback in the shared H2, so real action keys (user_created etc.) cannot be
        // asserted by absence — only these probes can.
        AuditLog probe = new AuditLog();
        probe.setAction("q_probe_action_alpha");
        probe.setActorName("q_probe@example.com");
        probe.setEntityType("User");
        entityManager.persist(probe);
        AuditLog other = new AuditLog();
        other.setAction("q_probe_action_beta");
        other.setActorName("q_probe@example.com");
        other.setEntityType("Role");
        entityManager.persist(other);

        // q reaches the action key — replaces the old exact-match action filter form.
        mockMvc.perform(get("/api/v1/audit-logs")
                        .param("q", "alpha")
                        .cookie(auth("reader@tenant.test", "iam:audit:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].action").value(hasItem("q_probe_action_alpha")))
                .andExpect(jsonPath("$.data[*].action").value(not(hasItem("q_probe_action_beta"))));
    }

    @Test
    void loginHistoryRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/login-history"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth_unauthenticated"));
    }

    @Test
    void loginHistoryForbiddenWithoutPermission() throws Exception {
        mockMvc.perform(get("/api/v1/login-history").cookie(auth("nop@tenant.test")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth_access_denied"));
    }

    @Test
    void loginHistoryReturnsSeededEntries() throws Exception {
        LoginHistory entry = new LoginHistory();
        entry.setUsername("login_seeded@example.com");
        entry.setSuccess(false);
        entry.setReason("auth_bad_credentials");
        entityManager.persist(entry);

        mockMvc.perform(get("/api/v1/login-history").cookie(auth("reader@tenant.test", "iam:audit:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].username").value(hasItem("login_seeded@example.com")));
    }

    @Test
    void loginHistoryFilterBySuccess() throws Exception {
        LoginHistory failed = new LoginHistory();
        failed.setUsername("login_failure_probe@example.com");
        failed.setSuccess(false);
        failed.setReason("auth_bad_credentials");
        entityManager.persist(failed);
        LoginHistory ok = new LoginHistory();
        ok.setUsername("login_success_probe@example.com");
        ok.setSuccess(true);
        entityManager.persist(ok);

        mockMvc.perform(get("/api/v1/login-history")
                        .param("success", "false")
                        .cookie(auth("reader@tenant.test", "iam:audit:read")))
                .andExpect(status().isOk())
                // The failure probe must appear and the success probe must not — proving the
                // success=false filter both includes failures and excludes successes, regardless
                // of other failed-login rows left by prior tests.
                .andExpect(jsonPath("$.data[*].username").value(hasItem("login_failure_probe@example.com")))
                .andExpect(jsonPath("$.data[*].username").value(not(hasItem("login_success_probe@example.com"))));
    }

    @Test
    void requestLogsSortByCreatedDateIsAllowed() throws Exception {
        // The SPA's default sort — the entity attribute name, NOT the DTO's createdAt.
        seedRequestLog("sort_probe_created", 100L);
        seedRequestLog("sort_probe_created", 200L);

        mockMvc.perform(get("/api/v1/request-logs")
                        .param("sort", "createdDate,desc")
                        .param("q", "sort_probe_created")
                        .cookie(auth("reader@tenant.test", "iam:audit:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.totalElements").value(2));
    }

    @Test
    void requestLogsSortByDurationMsOrdersAscending() throws Exception {
        seedRequestLog("sort_probe_duration", 200L);
        seedRequestLog("sort_probe_duration", 100L);

        mockMvc.perform(get("/api/v1/request-logs")
                        .param("sort", "durationMs,asc")
                        .param("q", "sort_probe_duration")
                        .cookie(auth("reader@tenant.test", "iam:audit:read")))
                .andExpect(status().isOk())
                // The q sentinel isolates the two probes, so the order is deterministic.
                .andExpect(jsonPath("$.meta.totalElements").value(2))
                .andExpect(jsonPath("$.data[0].durationMs").value(100))
                .andExpect(jsonPath("$.data[1].durationMs").value(200));
    }

    @Test
    void requestLogsRejectSortOutsideWhitelist() throws Exception {
        // The DTO wire name is NOT sortable — regression lock for the SPA bug where the
        // default sort sent createdAt and every request 400'd.
        mockMvc.perform(get("/api/v1/request-logs")
                        .param("sort", "createdAt")
                        .cookie(auth("reader@tenant.test", "iam:audit:read")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"));
    }

    /* ── K-49: newly registered columns (reason/userAgent/ipAddress) + INT status ── */

    @Test
    void loginHistoryFiltersByReason() throws Exception {
        LoginHistory locked = new LoginHistory();
        locked.setUsername("reason_locked@example.com");
        locked.setSuccess(false);
        locked.setReason("auth_account_locked");
        entityManager.persist(locked);
        LoginHistory bad = new LoginHistory();
        bad.setUsername("reason_bad@example.com");
        bad.setSuccess(false);
        bad.setReason("auth_bad_credentials");
        entityManager.persist(bad);

        mockMvc.perform(post("/api/v1/login-history/search")
                        .contentType(JSON)
                        .cookie(auth("reader@tenant.test", "iam:audit:read"))
                        .content("""
                                {"filters":[{"field":"reason","operator":"EQ","values":["auth_account_locked"]}]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].username").value(hasItem("reason_locked@example.com")))
                .andExpect(jsonPath("$.data[*].username").value(not(hasItem("reason_bad@example.com"))));
    }

    @Test
    void requestLogsFilterByIntStatus() throws Exception {
        com.ibrhalil.forgesys.entity.RequestLog error = seedRequestLogWithStatus("status_probe", 500);
        seedRequestLogWithStatus("status_probe", 200);

        mockMvc.perform(post("/api/v1/request-logs/search")
                        .contentType(JSON)
                        .cookie(auth("reader@tenant.test", "iam:audit:read"))
                        .content("""
                                {"filters":[{"field":"status","operator":"GTE","values":["400"]}]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.totalElements").value(1))
                .andExpect(jsonPath("$.data[0].traceId").value(error.getTraceId()));
    }

    private com.ibrhalil.forgesys.entity.RequestLog seedRequestLogWithStatus(String pathSentinel, int status) {
        com.ibrhalil.forgesys.entity.RequestLog entry = new com.ibrhalil.forgesys.entity.RequestLog();
        entry.setTraceId(java.util.UUID.randomUUID().toString());
        entry.setMethod("GET");
        entry.setPath(pathSentinel);
        entry.setStatus(status);
        entry.setDurationMs(100L);
        entityManager.persist(entry);
        return entry;
    }

    private void seedRequestLog(String pathSentinel, Long durationMs) {
        com.ibrhalil.forgesys.entity.RequestLog entry = new com.ibrhalil.forgesys.entity.RequestLog();
        entry.setTraceId(java.util.UUID.randomUUID().toString());
        entry.setMethod("GET");
        entry.setPath(pathSentinel);
        entry.setStatus(200);
        entry.setDurationMs(durationMs);
        entityManager.persist(entry);
    }
}
