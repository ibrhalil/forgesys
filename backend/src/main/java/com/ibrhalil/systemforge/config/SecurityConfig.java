package com.ibrhalil.systemforge.config;

import com.ibrhalil.systemforge.security.RestAccessDeniedHandler;
import com.ibrhalil.systemforge.security.RestAuthenticationEntryPoint;
import com.ibrhalil.systemforge.security.jwt.JwtAuthenticationFilter;
import com.ibrhalil.systemforge.tenant.TenantFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;


/**
 * Spring Security core setup (Epic 2.3). Stateless, CSRF-less API with cookie-based
 * JWT auth (tokens arrive in Chunk C/D). {@code /api/v1/auth/**} stays public (tenant
 * signup + login); everything else requires authentication.
 *
 * <p>{@link PasswordEncoder} uses BCrypt strength 12 (RISK-13). Existing strength-10
 * hashes still validate (BCrypt is self-describing — the cost factor is embedded in the
 * hash); new encodings use 12, so migration is lazy on the next password change.
 *
 * <p>{@link TenantFilter} is registered to run BEFORE the Spring Security filter chain
 * (security order -100) so that tenant context is resolved for every request before the
 * JWT authentication filter (Chunk C) executes.
 */
@Configuration
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
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
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

    /** The Spring Security filter chain (DelegatingFilterProxy) order. */
    private static final int SECURITY_FILTER_ORDER = -100;
}
