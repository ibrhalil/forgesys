package com.ibrhalil.forgesys;

import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * K-41: the springdoc endpoints are up in dev/test (default configuration) — the
 * OpenAPI spec is served, carries the {@code cookieAuth} security scheme and the
 * documented API paths, and the Swagger UI assets render. The prod profile disables
 * both via the {@code springdoc.*} flags (verified by {@link SpringdocDisabledTest}
 * at the property level — the prod profile itself cannot boot in tests: RSA keys
 * and datasource env are fail-fast by design).
 */
@SpringBootTest
@ActiveProfiles("test")
class SpringdocEndpointsTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Filter securityFilter = (Filter) context.getBean("springSecurityFilterChain");
        mockMvc = MockMvcBuilders.webAppContextSetup(context).addFilters(securityFilter).build();
    }

    @Test
    void apiDocsServedWithCookieAuthSchemeAndApiPaths() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.components.securitySchemes.cookieAuth").exists())
                .andExpect(jsonPath("$.components.securitySchemes.cookieAuth.type").value("apiKey"))
                .andExpect(jsonPath("$.components.securitySchemes.cookieAuth.in").value("cookie"))
                .andExpect(jsonPath("$.components.securitySchemes.cookieAuth.name").value("sf_access_token"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/login']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/users']").exists());
    }

    @Test
    void swaggerUiServed() throws Exception {
        // The canonical entry /swagger-ui.html redirects (302) to the UI bundle...
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection());
        // ...whose index page renders (200) through the security chain permitAll.
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }
}
