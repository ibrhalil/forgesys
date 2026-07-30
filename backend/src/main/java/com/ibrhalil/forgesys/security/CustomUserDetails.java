package com.ibrhalil.forgesys.security;

import com.ibrhalil.forgesys.entity.User;
import com.ibrhalil.forgesys.entity.UserAccount;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * Tenant-aware Spring Security principal. The auth identifier (Spring's
 * {@code username}) is the user's <strong>email</strong> (login is email-based).
 *
 * <p>Two construction paths:
 * <ul>
 *   <li>{@link CustomUserDetailsService} builds it from the DB at login (with real
 *       account flags and resolved authorities).</li>
 *   <li>{@code JwtAuthenticationFilter} rebuilds it from token claims on each
 *       request (no DB hit; account flags assumed valid for the short-lived token).</li>
 * </ul>
 */
public class CustomUserDetails implements UserDetails {

    private final UUID userId;
    private final String email;
    private final String password;
    private final boolean enabled;
    private final boolean accountNonExpired;
    private final boolean accountNonLocked;
    private final boolean credentialsNonExpired;
    private final Set<GrantedAuthority> authorities;
    private final String tenantSchema;
    private final String jti;

    public CustomUserDetails(UUID userId, String email, String password, boolean enabled,
                             boolean accountNonExpired, boolean accountNonLocked,
                             boolean credentialsNonExpired, Set<GrantedAuthority> authorities,
                             String tenantSchema, String jti) {
        this.userId = userId;
        this.email = email;
        this.password = password;
        this.enabled = enabled;
        this.accountNonExpired = accountNonExpired;
        this.accountNonLocked = accountNonLocked;
        this.credentialsNonExpired = credentialsNonExpired;
        this.authorities = authorities;
        this.tenantSchema = tenantSchema;
        this.jti = jti;
    }

    public static CustomUserDetails from(User user, UserAccount account, Set<GrantedAuthority> authorities, String tenantSchema) {
        return new CustomUserDetails(
                user.getId(),
                user.getEmail(),
                user.getPassword(),
                account.isEnabled(),
                account.isAccountNonExpired(),
                isEffectivelyNonLocked(account),
                account.isCredentialsNonExpired(),
                authorities,
                tenantSchema,
                null);
    }

    /**
     * [RISK-22] The brute-force lockout writes {@code lockedUntil} only — the
     * {@code accountNonLocked} column is never touched (it stays {@code true}). An
     * active lock window ({@code lockedUntil} in the future) must therefore make
     * {@code isAccountNonLocked()} false here, otherwise a locked account with a live
     * refresh token could keep minting fresh access tokens through
     * {@code /auth/refresh} for the whole lock duration — bypassing the lockout
     * (only the access tokens are killed via {@code tokenInvalidBefore}, and refresh
     * immediately issues new ones). Lock expiry is lazy: a past {@code lockedUntil}
     * still counts as non-locked (the login path clears it on the next attempt).
     */
    private static boolean isEffectivelyNonLocked(UserAccount account) {
        return account.isAccountNonLocked()
                && (account.getLockedUntil() == null || !account.getLockedUntil().isAfter(java.time.OffsetDateTime.now()));
    }

    public UUID getUserId() {
        return userId;
    }

    public String getTenantSchema() {
        return tenantSchema;
    }

    public String getEmail() {
        return email;
    }

    /**
     * The JWT id ({@code jti}) of the access token this principal was reconstructed from
     * (K-34). Used by per-session logout to blacklist the single token. {@code null} on
     * principals built at login time (before a token exists).
     */
    public String getJti() {
        return jti;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    /** Login is email-based; the Spring "username" is the email. */
    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return accountNonExpired;
    }

    @Override
    public boolean isAccountNonLocked() {
        return accountNonLocked;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return credentialsNonExpired;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
