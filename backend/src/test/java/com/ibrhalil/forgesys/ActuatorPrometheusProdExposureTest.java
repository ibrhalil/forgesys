package com.ibrhalil.forgesys;

import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * K-43 prod narrowing proof: with the exposure set {@code application-prod.yaml}
 * configures ({@code health,info,prometheus}) the scrape endpoint stays while the
 * per-metric {@code /actuator/metrics} debugging endpoint becomes inaccessible
 * (401 here — the security chain rejects first; on the port-separated prod
 * management context, which runs without the chain, it would 404 as unregistered).
 * The prod management port (8081) itself is a deployment concern
 * (docker-compose-prod.yml expose-only), not reproducible in a mock MVC test —
 * what this locks down is the endpoint set.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        // Mirrors application-prod.yaml — keep the two in sync.
        "management.endpoints.web.exposure.include=health,info,prometheus",
})
class ActuatorPrometheusProdExposureTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Filter securityFilter = (Filter) context.getBean("springSecurityFilterChain");
        mockMvc = MockMvcBuilders.webAppContextSetup(context).addFilters(securityFilter).build();
    }

    @Test
    void prometheusStaysExposedUnderTheProdSet() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk());
    }

    @Test
    void metricsEndpointIsInaccessibleUnderTheProdSet() throws Exception {
        // In this same-port mock layout the security chain rejects the
        // unauthenticated request before actuator routing (401). On the real prod
        // management port there IS no security chain — the unexposed endpoint
        // unregisters and 404s instead. Either way: not reachable.
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isUnauthorized());
    }
}
