package com.ibrhalil.forgesys.entity;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Locks the shared {@link EntityEqualityUtil} contract for BOTH id-bearing
 * hierarchies: exact-class comparison, id-based equality, and the frozen quirks
 * (a null id never equals anything; transient entities hash by identity).
 */
class EntityEqualityTest {

    // --- BaseEntity family ---

    @Test
    void baseEntitySameIdIsEqualAndSymmetric() {
        UUID id = UUID.randomUUID();
        Role first = roleWithId(id);
        Role second = roleWithId(id);

        assertThat(first).isEqualTo(second);
        assertThat(second).isEqualTo(first);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
    }

    @Test
    void baseEntityDistinctIdsAreNotEqual() {
        assertThat(roleWithId(UUID.randomUUID())).isNotEqualTo(roleWithId(UUID.randomUUID()));
    }

    @Test
    void baseEntityNullIdNeverEqualsAnotherNullId() {
        assertThat(new Role()).isNotEqualTo(new Role());

        Role role = new Role();
        assertThat(role).isEqualTo(role); // identity always equals
    }

    @Test
    void baseEntitySameIdDifferentConcreteClassIsNotEqual() {
        UUID id = UUID.randomUUID();
        Role role = roleWithId(id);
        Permission permission = new Permission();
        permission.setId(id);

        assertThat(role).isNotEqualTo(permission);
    }

    @Test
    void baseEntityDoesNotEqualNullOrOtherType() {
        Role role = roleWithId(UUID.randomUUID());

        assertThat(role).isNotEqualTo(null);
        assertThat(role).isNotEqualTo("not an entity");
    }

    // --- GeneratedIdAuditEntity family ---

    @Test
    void generatedIdEntitySameIdIsEqualAndSymmetric() {
        UUID id = UUID.randomUUID();
        TenantVerificationToken first = tokenWithId(id);
        TenantVerificationToken second = tokenWithId(id);

        assertThat(first).isEqualTo(second);
        assertThat(second).isEqualTo(first);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
    }

    @Test
    void generatedIdEntityDistinctIdsAreNotEqual() {
        assertThat(tokenWithId(UUID.randomUUID())).isNotEqualTo(tokenWithId(UUID.randomUUID()));
    }

    @Test
    void generatedIdEntityNullIdNeverEqualsAnotherNullId() {
        assertThat(new TenantVerificationToken()).isNotEqualTo(new TenantVerificationToken());

        TenantVerificationToken token = new TenantVerificationToken();
        assertThat(token).isEqualTo(token); // identity always equals
    }

    @Test
    void generatedIdEntitySameIdDifferentConcreteClassIsNotEqual() {
        UUID id = UUID.randomUUID();
        TenantVerificationToken token = tokenWithId(id);
        AuditLog auditLog = new AuditLog();
        auditLog.setId(id);

        assertThat(token).isNotEqualTo(auditLog);
    }

    private Role roleWithId(UUID id) {
        Role role = new Role();
        role.setId(id);
        return role;
    }

    private TenantVerificationToken tokenWithId(UUID id) {
        TenantVerificationToken token = new TenantVerificationToken();
        token.setId(id);
        return token;
    }
}
