package com.ibrhalil.systemforge.security;

import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security smoke tests (Epic 2.3): a protected resource requires authentication
 * (uniform 401), while {@code /api/v1/auth/**} is public (request reaches the
 * controller / validation layer instead of being rejected at the security layer).
 *
 * <p>Spring Boot 4.1 dropped {@code @AutoConfigureMockMvc} from the standard
 * autoconfigure, so MockMvc is wired manually and the Spring Security filter chain is
 * attached explicitly via {@code addFilters} (webAppContextSetup does not apply
 * servlet-registered filters on its own).
 */
@SpringBootTest
@ActiveProfiles("test")
class SecuritySmokeTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        Filter securityFilter = (Filter) context.getBean("springSecurityFilterChain");
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(securityFilter)
                .build();
    }

    @Test
    void protectedEndpointIsUnauthorizedWithUniformBody() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("auth_unauthenticated"))
                .andExpect(jsonPath("$.path").value("/api/v1/users"))
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    void authPrefixIsPublicAndReachesValidation() throws Exception {
        // permitAll: security lets the request through; the empty body then fails
        // bean validation (400), proving the security layer did not reject it (401).
        mockMvc.perform(post("/api/v1/auth/company/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"));
    }
}
