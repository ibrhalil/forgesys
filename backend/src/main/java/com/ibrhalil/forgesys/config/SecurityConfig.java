package com.ibrhalil.forgesys.config;

import com.ibrhalil.forgesys.security.PepperingPasswordEncoder;
import com.ibrhalil.forgesys.security.RestAccessDeniedHandler;
import com.ibrhalil.forgesys.security.RestAuthenticationEntryPoint;
import com.ibrhalil.forgesys.security.jwt.JwtAuthenticationFilter;
import com.ibrhalil.forgesys.tenant.TenantFilter;
import com.ibrhalil.forgesys.web.RequestMetadataFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.util.StringUtils;


/**
 * Spring Security core setup (Epic 2.3). Stateless, CSRF-less API with cookie-based
 * JWT auth (tokens arrive in Chunk C/D). {@code /api/v1/auth/**} stays public (tenant
 * signup + login); everything else requires authentication.
 *
 * <p>{@link PasswordEncoder} is a {@link PepperingPasswordEncoder}: BCrypt strength 12
 * (RISK-13) keyed with a global pepper via HMAC-SHA256 pre-hash (K-23). The pepper
 * defends against a standalone DB leak — the hashes are uncrackable without the
 * pepper, which lives outside the DB (env var / secret manager). Legacy pepper-less
 * BCrypt hashes (pre-K-23) still validate and are lazily rehashed to the peppered
 * format on the next successful login. The pepper is read from
 * {@code forgesys.security.password-pepper}; a blank value fails startup fast
 * (the {@code test}/{@code dev} profiles ship a non-secret default).
 *
 * <p>{@link TenantFilter} is registered to run BEFORE the Spring Security filter chain
 * (security order -100) so that tenant context is resolved for every request before the
 * JWT authentication filter (Chunk C) executes.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationFilter jwtAuthenticationFilter,
                                                   RestAuthenticationEntryPoint authenticationEntryPoint,
                                                   RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/company/**", "/api/v1/auth/login").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .anyRequest().authenticated())
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .contentTypeOptions(Customizer.withDefaults())
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; img-src 'self' data: https:; "
                                + "style-src 'self' 'unsafe-inline'; script-src 'self'; "
                                + "connect-src 'self'; frame-ancestors 'none'")))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder(
            @Value("${forgesys.security.password-pepper:}") String pepper) {
        if (!StringUtils.hasText(pepper)) {
            throw new IllegalStateException(
                    "forgesys.security.password-pepper is not set. " +
                    "Provide a non-blank secret via the PASSWORD_PEPPER env var " +
                    "(prod) or the forgesys.security.password-pepper property " +
                    "(the test/dev profiles ship a default).");
        }
        return new PepperingPasswordEncoder(pepper, 12);
    }

    /**
     * Runs {@link TenantFilter} before the Spring Security chain (the security
     * DelegatingFilterProxy registers at order -100), so the tenant schema is
     * resolved before authentication. Defining this registration also prevents the
     * default low-precedence auto-registration of the {@code @Component TenantFilter}.
     */
    @Bean
    public FilterRegistrationBean<TenantFilter> tenantFilterRegistration(TenantFilter tenantFilter) {
        FilterRegistrationBean<TenantFilter> registration = new FilterRegistrationBean<>(tenantFilter);
        registration.setOrder(SECURITY_FILTER_ORDER - 1);
        return registration;
    }

    /**
     * Runs {@link RequestMetadataFilter} before the tenant filter (order -102) and
     * the Spring Security chain (-100), so the trace id, client IP and User-Agent
     * are available to every downstream filter and service (and error responses
     * carry a stable trace id). Defining this registration also prevents the
     * default low-precedence auto-registration of the {@code @Component} filter.
     */
    @Bean
    public FilterRegistrationBean<RequestMetadataFilter> requestMetadataFilterRegistration(
            RequestMetadataFilter requestMetadataFilter) {
        FilterRegistrationBean<RequestMetadataFilter> registration = new FilterRegistrationBean<>(requestMetadataFilter);
        registration.setOrder(SECURITY_FILTER_ORDER - 2);
        return registration;
    }

    /** The Spring Security filter chain (DelegatingFilterProxy) order. */
    private static final int SECURITY_FILTER_ORDER = -100;
}
