package com.ibrhalil.forgesys;

import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * K-43 metrics exposure proof (dev/test layout — same port): {@code /actuator/prometheus}
 * renders the text exposition format with the auto-configured JVM series plus the
 * {@code forgesys.tenants.active} business gauge, and the per-metric
 * {@code /actuator/metrics} endpoint answers. Runs the real security filter chain —
 * the scrape endpoint is intentionally unauthenticated (SecurityConfig permitAll).
 */
@SpringBootTest
@ActiveProfiles("test")
class ActuatorPrometheusTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Filter securityFilter = (Filter) context.getBean("springSecurityFilterChain");
        mockMvc = MockMvcBuilders.webAppContextSetup(context).addFilters(securityFilter).build();
    }

    @Test
    void prometheusRendersTextFormatWithJvmAndTenantSeries() throws Exception {
        MvcResult result = mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        // Auto-configured JVM series + the K-43 business gauge (dots render as underscores).
        assertThat(body).contains("jvm_memory_used_bytes");
        assertThat(body).contains("forgesys_tenants_active");
    }

    @Test
    void metricsEndpointIsExposedButRequiresAuthentication() throws Exception {
        // Deliberate asymmetry: only health/info/prometheus are anonymous in the
        // same-port layout — the per-metric debugging endpoint stays behind auth.
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isUnauthorized());
    }
}
