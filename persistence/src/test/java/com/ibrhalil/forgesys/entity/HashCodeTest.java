package com.ibrhalil.forgesys.entity;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HashCodeTest {

    @Test
    void baseEntitiesWithDistinctIdsDoNotCollideInSet() {
        Role first = roleWithId(UUID.randomUUID());
        Role second = roleWithId(UUID.randomUUID());

        Set<Role> roles = new HashSet<>();
        roles.add(first);
        roles.add(second);

        assertThat(roles).hasSize(2);
    }

    @Test
    void baseEntitiesWithSameIdCollapseToOneInSet() {
        UUID id = UUID.randomUUID();
        Role first = roleWithId(id);
        Role second = roleWithId(id);

        Set<Role> roles = new HashSet<>();
        roles.add(first);
        roles.add(second);

        assertThat(roles).hasSize(1);
    }

    @Test
    void generatedIdEntitiesWithDistinctIdsDoNotCollideInSet() {
        RefreshToken first = refreshTokenWithId(UUID.randomUUID());
        RefreshToken second = refreshTokenWithId(UUID.randomUUID());

        Set<RefreshToken> tokens = new HashSet<>();
        tokens.add(first);
        tokens.add(second);

        assertThat(tokens).hasSize(2);
    }

    @Test
    void transientEntitiesHaveStableHashCodeAcrossCalls() {
        Role transientEntity = new Role();

        int firstHash = transientEntity.hashCode();
        int secondHash = transientEntity.hashCode();

        assertThat(firstHash).isEqualTo(secondHash);
    }

    private Role roleWithId(UUID id) {
        Role role = new Role();
        role.setId(id);
        return role;
    }

    private RefreshToken refreshTokenWithId(UUID id) {
        RefreshToken token = new RefreshToken();
        token.setId(id);
        return token;
    }
}
