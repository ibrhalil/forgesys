package com.ibrhalil.forgesys.security;

import com.ibrhalil.forgesys.entity.User;
import com.ibrhalil.forgesys.entity.UserAccount;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * Tenant-aware principal; Spring's "username" is the user's email (login is email-based).
 * Built from the DB at login; rebuilt from JWT claims per request (no DB hit).
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
     * [RISK-22] Lockout writes {@code lockedUntil} only, so an active window must count
     * as locked here — otherwise a locked account with a live refresh token could keep
     * minting access tokens via /auth/refresh for the whole lock duration. Expiry is
     * lazy (past {@code lockedUntil} = non-locked; login clears it on next attempt).
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

    /** jti of the token this principal was rebuilt from (K-34, per-session logout); null at login time. */
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
