package com.ibrhalil.forgesys.controller;

import com.ibrhalil.forgesys.entity.Company;
import com.ibrhalil.forgesys.entity.CompanyStatus;
import com.ibrhalil.forgesys.tenant.TenantFilter;
import com.ibrhalil.forgesys.web.RequestBodyCaptureFilter;
import com.ibrhalil.forgesys.web.RequestLogFilter;
import com.ibrhalil.forgesys.web.RequestMetadataFilter;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression lock for the request-log write path (K-19 layer 3): the filters run
 * in the REAL registration order (metadata -102, tenant -101, security -100,
 * request-log -95, body-capture -94), so the {@code t_request_logs} row is written
 * while the tenant/auth/metadata ThreadLocals are still live. The original bug had
 * {@code @Order(HIGHEST_PRECEDENCE + n)} on the filters, which placed them OUTSIDE
 * the whole chain — the write landed after every context was cleared (public schema,
 * null user/trace) and the failing insert was swallowed at DEBUG level, leaving the
 * request-logs screen permanently empty.
 */
@SpringBootTest
@ActiveProfiles("test")
@org.springframework.transaction.annotation.Transactional
class RequestLogFilterChainTest extends AbstractRbacWebTest {

    private static final String SUBDOMAIN = "reqlogtest";
    private static final String HOST = SUBDOMAIN + ".localhost";

    @Autowired
    private RequestMetadataFilter requestMetadataFilter;
    @Autowired
    private TenantFilter tenantFilter;
    @Autowired
    private RequestLogFilter requestLogFilter;
    @Autowired
    private RequestBodyCaptureFilter requestBodyCaptureFilter;

    private MockMvc chainMvc;

    @BeforeEach
    void buildRealOrderChain() {
        // Same relative order the FilterRegistrationBeans define in SecurityConfig:
        // outermost first. This exercises the write path exactly as the servlet
        // container would.
        Filter securityFilter = (Filter) context.getBean("springSecurityFilterChain");
        chainMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(requestMetadataFilter, tenantFilter, securityFilter, requestLogFilter, requestBodyCaptureFilter)
                .build();

        // Tenant resolution target: an ACTIVE company whose schema is H2's PUBLIC
        // schema (the test convention — TenantContext unset resolves to "public"),
        // so the request-log row lands where the shared H2 reads it back.
        Company company = new Company();
        company.setName("Request Log Test Co");
        company.setSubdomain(SUBDOMAIN);
        company.setSchemaName("public");
        company.setStatus(CompanyStatus.ACTIVE);
        entityManager.persist(company);
        entityManager.flush();
    }

    @Test
    void writesRowWithLiveContexts() throws Exception {
        String traceId = "reqlog-" + UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String email = "reqlog.probe@tenant.test";

        // First request through the full chain — its row is written in the filter's
        // finally, after the response is rendered.
        chainMvc.perform(get("/api/v1/request-logs")
                        .header("Host", HOST)
                        .header("X-Request-Id", traceId)
                        .header("User-Agent", "reqlog-agent-probe/1.0")
                        .cookie(auth(userId, email, "iam:audit:read")))
                .andExpect(status().isOk());

        // Second request reads the first one's row back via the traceId filter.
        chainMvc.perform(get("/api/v1/request-logs")
                        .param("traceId", traceId)
                        .header("Host", HOST)
                        .header("X-Request-Id", traceId + "-readback")
                        .cookie(auth(userId, email, "iam:audit:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.totalElements").value(1))
                .andExpect(jsonPath("$.data[0].traceId").value(traceId))
                .andExpect(jsonPath("$.data[0].method").value("GET"))
                .andExpect(jsonPath("$.data[0].path").value("/api/v1/request-logs"))
                .andExpect(jsonPath("$.data[0].status").value(200))
                // Contexts live at write time — these were null before the fix.
                .andExpect(jsonPath("$.data[0].userId").value(userId.toString()))
                .andExpect(jsonPath("$.data[0].username").value(email))
                .andExpect(jsonPath("$.data[0].ipAddress").value("127.0.0.1"))
                .andExpect(jsonPath("$.data[0].userAgent").value("reqlog-agent-probe/1.0"));
    }

    @Test
    void skipsWriteWhenNoTenantResolved() throws Exception {
        String traceId = "reqlog-skip-" + UUID.randomUUID();

        // Actuator is tenant-exempt (TenantFilter shouldNotFilter) — no tenant schema,
        // the write must be skipped instead of failing an insert into "public".
        chainMvc.perform(get("/actuator/health")
                        .header("X-Request-Id", traceId))
                .andExpect(status().isOk());

        chainMvc.perform(get("/api/v1/request-logs")
                        .param("traceId", traceId)
                        .header("Host", HOST)
                        .cookie(auth("reqlog.probe@tenant.test", "iam:audit:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.totalElements").value(0));
    }

    @Test
    void filtersAreRegisteredInsideTheSecurityChain() {
        // The registration beans ARE the fix: the filters must not float on their
        // @Component auto-registration (lowest precedence / outside the chain).
        assertOrder(RequestMetadataFilter.class, -102);
        assertOrder(TenantFilter.class, -101);
        assertOrder(RequestLogFilter.class, -95);
        assertOrder(RequestBodyCaptureFilter.class, -94);
    }

    @SuppressWarnings("rawtypes")
    private void assertOrder(Class<?> filterType, int expectedOrder) {
        Integer order = context.getBeansOfType(FilterRegistrationBean.class).values().stream()
                .filter(r -> filterType.isInstance(r.getFilter()))
                .map(FilterRegistrationBean::getOrder)
                .findFirst()
                .orElse(null);
        assertNotNull(order, filterType.getSimpleName() + " must be registered via FilterRegistrationBean");
        assertEquals(expectedOrder, order.intValue(), filterType.getSimpleName() + " registration order");
    }
}
