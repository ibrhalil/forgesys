package com.ibrhalil.forgesys.controller;

import com.ibrhalil.forgesys.entity.PlatformAuditLog;
import com.ibrhalil.forgesys.persistence.repository.PlatformAuditLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * K-50 F7 platform audit read: gates (platform scope + {@code platform:audit:read}),
 * paged newest-first listing, simple filters (action/actorId/targetType/date range),
 * {@code q} search and the sort whitelist. H2; sentinels are unique per test (the
 * shared cached-context H2 also carries REQUIRES_NEW audit rows from other tests).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PlatformAuditControllerTest extends AbstractRbacWebTest {

    private static final String BASE = "/api/v1/platform/audit-logs";

    @Autowired
    PlatformAuditLogRepository platformAuditLogRepository;

    /* ── auth / permission gates ── */

    @Test
    void listRequiresAuthentication() throws Exception {
        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
    }

    @Test
    void listForbiddenWithoutAuditRead() throws Exception {
        mockMvc.perform(get(BASE).cookie(authPlatform(UUID.randomUUID(), "reader@platform.test",
                        List.of("platform:company:read"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth_access_denied"));
    }

    /** RISK-18 posture: a tenant JWT never reaches the platform surface, legacy authority or not. */
    @Test
    void listForbiddenForTenantJwtEvenWithAuthority() throws Exception {
        mockMvc.perform(get(BASE).cookie(auth("admin@tenant.test", "platform:audit:read")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth_access_denied"));
    }

    /* ── listing + filters ── */

    @Test
    void listFiltersByActionAndReturnsNewestFirst() throws Exception {
        String action = "ut_audit_" + UUID.randomUUID();
        seed(action, UUID.randomUUID(), "ut_target", "first");
        Thread.sleep(15); // distinct createdDate (auditing stamps now())
        seed(action, UUID.randomUUID(), "ut_target", "second");

        String body = mockMvc.perform(get(BASE).param("action", action)
                        .cookie(authPlatform(UUID.randomUUID(), "root@platform.test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.totalElements").value(2))
                .andReturn().getResponse().getContentAsString();

        int firstPos = body.indexOf("\"first\"");
        int secondPos = body.indexOf("\"second\"");
        assertThat(secondPos).isLessThan(firstPos); // newest first
    }

    @Test
    void listFiltersByActorId() throws Exception {
        String action = "ut_audit_" + UUID.randomUUID();
        UUID actorA = UUID.randomUUID();
        seed(action, actorA, "ut_target", "by-a");
        seed(action, UUID.randomUUID(), "ut_target", "by-b");

        mockMvc.perform(get(BASE).param("action", action).param("actorId", actorA.toString())
                        .cookie(authPlatform(UUID.randomUUID(), "root@platform.test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.totalElements").value(1))
                .andExpect(jsonPath("$.data[0].detail").value("by-a"));
    }

    @Test
    void listFiltersByTargetTypeAndQ() throws Exception {
        String target = "ut_target_" + UUID.randomUUID();
        seed("ut_audit_" + UUID.randomUUID(), UUID.randomUUID(), target, "matched");

        mockMvc.perform(get(BASE).param("targetType", target)
                        .cookie(authPlatform(UUID.randomUUID(), "root@platform.test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.totalElements").value(1))
                .andExpect(jsonPath("$.data[0].targetType").value(target));

        mockMvc.perform(get(BASE).param("q", target)
                        .cookie(authPlatform(UUID.randomUUID(), "root@platform.test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].targetType").value(target));
    }

    @Test
    void listFiltersByDateRange() throws Exception {
        String action = "ut_audit_" + UUID.randomUUID();
        seed(action, UUID.randomUUID(), "ut_target", "range");

        // Future-bounded range excludes the fresh row; past→future range includes it.
        String future = OffsetDateTime.now().plusHours(1).toString();
        String past = OffsetDateTime.now().minusHours(1).toString();
        mockMvc.perform(get(BASE).param("action", action).param("fromDate", future)
                        .cookie(authPlatform(UUID.randomUUID(), "root@platform.test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.totalElements").value(0));
        mockMvc.perform(get(BASE).param("action", action)
                        .param("fromDate", past).param("toDate", future)
                        .cookie(authPlatform(UUID.randomUUID(), "root@platform.test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.totalElements").value(1));
    }

    @Test
    void listRejectsUnknownSortProperty() throws Exception {
        mockMvc.perform(get(BASE).param("sort", "detail")
                        .cookie(authPlatform(UUID.randomUUID(), "root@platform.test")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"));
    }

    /* ── helpers ── */

    private void seed(String action, UUID actorId, String targetType, String detail) {
        PlatformAuditLog entry = new PlatformAuditLog();
        entry.setActorId(actorId);
        entry.setActorType("HUMAN");
        entry.setAction(action);
        entry.setTargetType(targetType);
        entry.setTargetId(UUID.randomUUID());
        entry.setDetail(detail);
        platformAuditLogRepository.saveAndFlush(entry);
    }
}
