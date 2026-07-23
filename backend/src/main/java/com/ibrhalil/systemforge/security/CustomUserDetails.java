package com.ibrhalil.systemforge.security;

import com.ibrhalil.systemforge.entity.User;
import com.ibrhalil.systemforge.entity.UserAccount;
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

    public CustomUserDetails(UUID userId, String email, String password, boolean enabled,
                             boolean accountNonExpired, boolean accountNonLocked,
                             boolean credentialsNonExpired, Set<GrantedAuthority> authorities,
                             String tenantSchema) {
        this.userId = userId;
        this.email = email;
        this.password = password;
        this.enabled = enabled;
        this.accountNonExpired = accountNonExpired;
        this.accountNonLocked = accountNonLocked;
        this.credentialsNonExpired = credentialsNonExpired;
        this.authorities = authorities;
        this.tenantSchema = tenantSchema;
    }

    public static CustomUserDetails from(User user, UserAccount account, Set<GrantedAuthority> authorities, String tenantSchema) {
        return new CustomUserDetails(
                user.getId(),
                user.getEmail(),
                user.getPassword(),
                account.isEnabled(),
                account.isAccountNonExpired(),
                account.isAccountNonLocked(),
                account.isCredentialsNonExpired(),
                authorities,
                tenantSchema);
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
