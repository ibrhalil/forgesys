package com.ibrhalil.forgesys.controller;

import com.ibrhalil.forgesys.entity.AuditLog;
import com.ibrhalil.forgesys.entity.LoginHistory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuditControllerTest extends AbstractRbacWebTest {

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
                .andExpect(jsonPath("$.content[*].actorName").value(hasItem("audit_seeded_actor@example.com")));
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
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].action").value("test_probe_audit_filter"));
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
                .andExpect(jsonPath("$.content[*].username").value(hasItem("login_seeded@example.com")));
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
                .andExpect(jsonPath("$.content[*].username").value(hasItem("login_failure_probe@example.com")))
                .andExpect(jsonPath("$.content[*].username").value(not(hasItem("login_success_probe@example.com"))));
    }
}
