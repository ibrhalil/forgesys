package com.ibrhalil.forgesys.security;

import com.ibrhalil.forgesys.common.tenant.TenantContext;
import com.ibrhalil.forgesys.entity.Group;
import com.ibrhalil.forgesys.entity.Permission;
import com.ibrhalil.forgesys.entity.Role;
import com.ibrhalil.forgesys.entity.User;
import com.ibrhalil.forgesys.entity.UserAccount;
import com.ibrhalil.forgesys.entity.UserProfile;
import com.ibrhalil.forgesys.persistence.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies authority resolution in {@link CustomUserDetailsService} against a real
 * repository (H2, test profile). This is the regression guard for the lazy-collection
 * bug: the previous mock-based test hand-populated in-memory collections and so never
 * exercised the DB fetch path, masking the fact that group / inherited permissions did
 * not reach the token. Effective authorities must come from direct user roles + active
 * group roles (+ transitive parent roles), while inactive groups are skipped.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CustomUserDetailsServiceTest {

    @PersistenceContext
    EntityManager entityManager;

    @Autowired
    UserRepository userRepository;

    @Autowired
    CustomUserDetailsService userDetailsService;

    @BeforeEach
    void setUp() {
        // Test profile: TenantContext unset -> resolver returns "public" (H2 PUBLIC
        // schema), so seeded entities are reachable.
        TenantContext.setCurrentTenant("public");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void resolvesPermissionsFromDirectRolesAndActiveGroups() {
        Permission read = permission("tasks:task:read");
        Permission write = permission("iam:user:write");
        Permission secret = permission("billing:invoice:read");

        Role memberRole = role("member", Set.of(read));
        Role adminRole = role("admin", Set.of(write));
        Role billingRole = role("billing", Set.of(secret));

        Group activeGroup = group("admins", true, Set.of(adminRole));
        Group inactiveGroup = group("excluded", false, Set.of(billingRole));

        User user = seedUser("admin@acme.com", Set.of(memberRole), Set.of(activeGroup, inactiveGroup));

        Set<String> authorities = resolveAuthorities(user.getId());

        // direct (tasks:task:read) + active-group (iam:user:write); inactive group's
        // billing permission is excluded.
        assertThat(authorities).containsExactlyInAnyOrder("tasks:task:read", "iam:user:write");
    }

    @Test
    void resolvesPermissionsTransitivelyInheritedFromParentRoles() {
        // Faz 4a: a role inherits its parent roles' permissions transitively.
        // grandparent -> a:read ; parent -> b:read ; child -> c:read
        Role grandparent = role("gp", Set.of(permission("a:read")));
        Role parent = role("p", Set.of(permission("b:read")));
        parent.getParentRoles().add(grandparent);
        Role child = role("c", Set.of(permission("c:read")));
        child.getParentRoles().add(parent);

        User user = seedUser("u@acme.com", Set.of(child), Set.of());

        assertThat(resolveAuthorities(user.getId()))
                .containsExactlyInAnyOrder("a:read", "b:read", "c:read");
    }

    @Test
    void inheritanceCycleTerminatesViaVisitedGuard() {
        // Defense-in-depth: a malformed cycle (which setParents prevents) must not
        // infinite-loop — the visited set breaks it.
        Role a = role("a", Set.of(permission("a:x")));
        Role b = role("b", Set.of(permission("b:y")));
        a.getParentRoles().add(b);
        b.getParentRoles().add(a); // a -> b -> a

        User user = seedUser("cyc@acme.com", Set.of(a), Set.of());

        assertThat(resolveAuthorities(user.getId())).containsExactlyInAnyOrder("a:x", "b:y");
    }

    @Test
    void groupRolePermissionsAreResolved() {
        // The core regression: a user with ONLY a group role (no direct roles) must
        // still receive that group role's permissions. This is the case that was broken
        // by the nested-lazy-collection traversal.
        Permission groupPerm = permission("iam:role:read");
        Role groupRole = role("viewer", Set.of(groupPerm));
        Group group = group("readers", true, Set.of(groupRole));

        User user = seedUser("grouponly@acme.com", Set.of(), Set.of(group));

        assertThat(resolveAuthorities(user.getId())).containsExactly("iam:role:read");
    }

    @Test
    void unknownEmailThrowsUsernameNotFound() {
        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("nobody@acme.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    /* ── [RISK-22] lockout vs refresh (CustomUserDetails.from) ── */

    @Test
    void activeLockMakesPrincipalAccountNonLockedFalse() {
        // The refresh path re-resolves the principal via loadUserByUsername -> from();
        // an active lock window must surface as accountNonLocked=false there, or a
        // locked account could mint fresh access tokens through /auth/refresh for the
        // whole lock duration (the column itself is never written by the lockout).
        User user = seedUser("locked@acme.com", Set.of(), Set.of());
        user.getUserAccount().setLockedUntil(java.time.OffsetDateTime.now().plusMinutes(10));
        entityManager.merge(user);
        entityManager.flush();

        CustomUserDetails principal = userDetailsService.loadUserByUsername("locked@acme.com");

        assertThat(principal.isAccountNonLocked()).isFalse();
    }

    @Test
    void expiredLockStillCountsAsNonLocked() {
        // Lock expiry is lazy — a past lockedUntil stays in the row until the next
        // login attempt / admin unlock, but must NOT keep the account locked.
        User user = seedUser("expired@acme.com", Set.of(), Set.of());
        user.getUserAccount().setLockedUntil(java.time.OffsetDateTime.now().minusMinutes(1));
        entityManager.merge(user);
        entityManager.flush();

        CustomUserDetails principal = userDetailsService.loadUserByUsername("expired@acme.com");

        assertThat(principal.isAccountNonLocked()).isTrue();
    }

    private Set<String> resolveAuthorities(UUID userId) {
        return userDetailsService.resolveAuthorities(userId).stream()
                .map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
    }

    /* ── seeding helpers ── */

    private Permission permission(String name) {
        Permission p = new Permission();
        p.setName(name);
        entityManager.persist(p);
        return p;
    }

    private Role role(String name, Set<Permission> permissions) {
        Role r = new Role();
        r.setName(name);
        r.setPermissions(new java.util.HashSet<>(permissions));
        entityManager.persist(r);
        return r;
    }

    private Group group(String name, boolean active, Set<Role> roles) {
        Group g = new Group();
        g.setName(name);
        g.setActive(active);
        g.setRoles(new java.util.HashSet<>(roles));
        entityManager.persist(g);
        return g;
    }

    private User seedUser(String email, Set<Role> roles, Set<Group> groups) {
        User user = new User();
        user.setUsername(email);
        user.setEmail(email);
        user.setPassword("hash");
        user.setEmailVerified(false);
        user.setRoles(new java.util.HashSet<>(roles));
        user.setGroups(new java.util.HashSet<>(groups));

        UserAccount account = new UserAccount();
        account.setEnabled(true);
        account.setUser(user);
        user.setUserAccount(account);

        UserProfile profile = new UserProfile();
        profile.setUser(user);
        user.setUserProfile(profile);

        entityManager.persist(user);
        return user;
    }
}
