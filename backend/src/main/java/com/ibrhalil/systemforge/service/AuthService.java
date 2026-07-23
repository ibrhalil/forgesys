package com.ibrhalil.systemforge.service;

import com.ibrhalil.systemforge.dto.LoginRequest;
import com.ibrhalil.systemforge.dto.LoginResponse;
import com.ibrhalil.systemforge.exception.AuthException;
import com.ibrhalil.systemforge.security.CustomUserDetails;
import com.ibrhalil.systemforge.security.CustomUserDetailsService;
import com.ibrhalil.systemforge.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Authentication operations. {@link #login(LoginRequest)} validates credentials
 * against the tenant's user store (tenant resolved by {@code TenantFilter}) and
 * mints an RS256 access token.
 *
 * <p>Both an unknown email and a wrong password map to {@code auth_bad_credentials}
 * — the failure reason is never leaked (no user-enumeration oracle).
 *
 * <p>Deferred to the next session (Epic 2.5/2.6): refresh tokens, logout (Redis
 * blacklist), register (email-domain check), login-history write, brute-force
 * lockout (the {@code failedLoginAttempts}/{@code lockedUntil} fields already exist).
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final CustomUserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        CustomUserDetails user = loadUserOrFail(request.email());
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw AuthException.badCredentials();
        }
        List<String> authorities = user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        String token = tokenProvider.generateAccessToken(
                user.getUserId().toString(), user.getEmail(), user.getTenantSchema(), authorities);
        long expiresIn = tokenProvider.getAccessTokenTtlMinutes() * 60;
        return new LoginResponse(token, "Bearer", expiresIn, user.getUserId(), user.getEmail(), authorities);
    }

    private CustomUserDetails loadUserOrFail(String email) {
        try {
            return (CustomUserDetails) userDetailsService.loadUserByUsername(email);
        } catch (UsernameNotFoundException e) {
            throw AuthException.badCredentials();
        }
    }
}
