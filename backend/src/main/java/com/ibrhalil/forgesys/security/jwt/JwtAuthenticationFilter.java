package com.ibrhalil.forgesys.security.jwt;

import com.ibrhalil.forgesys.security.CustomUserDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Authenticates each request from the access-token cookie (RS256). Decodes the JWT,
 * rebuilds the {@link CustomUserDetails} principal + authorities from claims (no DB
 * hit), and populates the {@link SecurityContext}.
 *
 * <p>On any decode failure (bad signature/expired/malformed) the context is cleared
 * and the request proceeds unauthenticated; protected routes then get a uniform 401
 * from {@code RestAuthenticationEntryPoint}.
 *
 * <p>Revocation (DB {@code tokenInvalidBefore} + Redis blacklist) is deferred to the
 * logout/refresh work ([ROADMAP Epic 2.5/2.6](../../../../../../docs/ROADMAP.md)) —
 * this filter validates signature + expiry only for the first-working-login slice.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtDecoder jwtDecoder;
    private final String cookieName;

    public JwtAuthenticationFilter(JwtDecoder jwtDecoder,
                                   @Value("${jwt.cookie-name:sf_access_token}") String cookieName) {
        this.jwtDecoder = jwtDecoder;
        this.cookieName = cookieName;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = extractToken(request);
        if (StringUtils.hasText(token) && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Jwt jwt = jwtDecoder.decode(token);
                SecurityContextHolder.getContext().setAuthentication(toAuthentication(jwt));
            } catch (JwtException e) {
                log.debug("Invalid JWT rejected: {}", e.getMessage());
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        // 1. Cookie-based auth (browser sessions)
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            String cookieToken = Arrays.stream(cookies)
                    .filter(c -> cookieName.equals(c.getName()))
                    .map(Cookie::getValue)
                    .findFirst()
                    .orElse(null);
            if (cookieToken != null) {
                return cookieToken;
            }
        }
        // 2. Bearer token header (API clients, cURL, jobs)
        String bearerHeader = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerHeader) && bearerHeader.startsWith("Bearer ")) {
            return bearerHeader.substring(7);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private UsernamePasswordAuthenticationToken toAuthentication(Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        String email = jwt.getClaimAsString(JwtTokenProvider.CLAIM_EMAIL);
        String tenant = jwt.getClaimAsString(JwtTokenProvider.CLAIM_TENANT);
        List<String> authorityNames = jwt.getClaimAsStringList(JwtTokenProvider.CLAIM_AUTHORITIES);
        Set<GrantedAuthority> authorities = (authorityNames == null ? List.<String>of() : authorityNames).stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet());

        CustomUserDetails principal = new CustomUserDetails(userId, email, null, true, true, true, true, authorities, tenant);
        return new UsernamePasswordAuthenticationToken(principal, null, authorities);
    }
}
