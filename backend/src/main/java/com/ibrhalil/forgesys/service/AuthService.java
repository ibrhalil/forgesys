package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.dto.LoginRequest;
import com.ibrhalil.forgesys.dto.LoginResponse;
import com.ibrhalil.forgesys.entity.User;
import com.ibrhalil.forgesys.exception.AuthException;
import com.ibrhalil.forgesys.persistence.repository.UserRepository;
import com.ibrhalil.forgesys.security.CustomUserDetails;
import com.ibrhalil.forgesys.security.CustomUserDetailsService;
import com.ibrhalil.forgesys.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Authentication operations. {@link #login(LoginRequest)} validates credentials
 * against the tenant's user store (tenant resolved by {@code TenantFilter}) and
 * mints an RS256 access token.
 *
 * <p>Both an unknown email and a wrong password map to {@code auth_bad_credentials}
 * — the failure reason is never leaked (no user-enumeration oracle).
 *
 * <p><strong>Lazy pepper migration (K-23):</strong> a successful login whose stored
 * hash is a legacy pepper-less BCrypt hash is silently rehashed to the peppered
 * format and persisted. The transaction is read-write to allow that write; the
 * common case (already-peppered hash) performs no write.
 *
 * <p>Deferred to the next session (Epic 2.5/2.6): refresh tokens, logout (Redis
 * blacklist), tenant içi user register (domain-bazlı, K-21 org-domain tablosu), login-history write, brute-force
 * lockout (the {@code failedLoginAttempts}/{@code lockedUntil} fields already exist).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final CustomUserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;

    @Transactional
    public LoginResponse login(LoginRequest request) {
        CustomUserDetails user = loadUserOrFail(request.email());
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw AuthException.badCredentials();
        }
        upgradeHashIfNeeded(user.getUserId(), user.getPassword(), request.password());
        List<String> authorities = user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        String token = tokenProvider.generateAccessToken(
                user.getUserId().toString(), user.getEmail(), user.getTenantSchema(), authorities);
        long expiresIn = tokenProvider.getAccessTokenTtlMinutes() * 60;
        return new LoginResponse(token, "Bearer", expiresIn, user.getUserId(), user.getEmail(), authorities);
    }

    /**
     * Lazily migrates a legacy pepper-less hash to the peppered format (K-23) on the
     * first successful login after the encoder change. No-op for hashes that already
     * carry the pepper marker.
     */
    private void upgradeHashIfNeeded(UUID userId, String storedHash, CharSequence rawPassword) {
        if (!passwordEncoder.upgradeEncoding(storedHash)) {
            return;
        }
        userRepository.findById(userId).ifPresent(user -> {
            user.setPassword(passwordEncoder.encode(rawPassword));
            userRepository.save(user);
            log.info("Rehashed legacy password to peppered format for user {}", userId);
        });
    }

    private CustomUserDetails loadUserOrFail(String email) {
        try {
            return (CustomUserDetails) userDetailsService.loadUserByUsername(email);
        } catch (UsernameNotFoundException e) {
            throw AuthException.badCredentials();
        }
    }
}
