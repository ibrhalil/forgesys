package com.ibrhalil.forgesys.security;

import com.ibrhalil.forgesys.entity.Ownable;
import com.ibrhalil.forgesys.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Faz 4b: the ownership/ABAC template. Owns = passes; not-owned / unowned / null = 403.
 */
class OwnershipGuardTest {

    private final OwnershipGuard guard = new OwnershipGuard();

    @Test
    void ownerPasses() {
        UUID owner = UUID.randomUUID();
        assertThatCode(() -> guard.assertOwner(() -> owner, owner)).doesNotThrowAnyException();
    }

    @Test
    void differentOwnerIsDenied() {
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        assertThatThrownBy(() -> guard.assertOwner(() -> owner, other))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void unownedRecordIsDenied() {
        Ownable unowned = () -> null;
        assertThatThrownBy(() -> guard.assertOwner(unowned, UUID.randomUUID()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void nullEntityIsDenied() {
        assertThatThrownBy(() -> guard.assertOwner(null, UUID.randomUUID()))
                .isInstanceOf(BusinessException.class);
    }
}
