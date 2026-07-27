package com.ibrhalil.forgesys.controller;

import com.ibrhalil.forgesys.entity.AuditLog;
import com.ibrhalil.forgesys.entity.LoginHistory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

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
        entry.setActorName("admin@example.com");
        entry.setEntityType("User");
        entityManager.persist(entry);

        mockMvc.perform(get("/api/v1/audit-logs").cookie(auth("reader@tenant.test", "iam:audit:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].action").value("user_created"))
                .andExpect(jsonPath("$.content[0].actorName").value("admin@example.com"))
                .andExpect(jsonPath("$.content[0].entityType").value("User"));
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
        entry.setUsername("user@example.com");
        entry.setSuccess(false);
        entry.setReason("auth_bad_credentials");
        entityManager.persist(entry);

        mockMvc.perform(get("/api/v1/login-history").cookie(auth("reader@tenant.test", "iam:audit:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].username").value("user@example.com"))
                .andExpect(jsonPath("$.content[0].success").value(false))
                .andExpect(jsonPath("$.content[0].reason").value("auth_bad_credentials"));
    }
}
