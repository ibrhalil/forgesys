package com.ibrhalil.forgesys.entity;

/**
 * Shared equals/hashCode contract for the id-bearing superclasses. Semantics are frozen:
 * exact-class comparison (never instanceof — Hibernate proxy tolerance is unchanged),
 * a null id never equals, null id hashes by identity.
 */
public final class EntityEqualityUtil {

    private EntityEqualityUtil() {
    }

    public static boolean entityEquals(IdentifiableUuid self, Object other, Class<?> exactClass) {
        if (self == other) return true;
        if (other == null || exactClass != other.getClass()) return false;

        IdentifiableUuid that = (IdentifiableUuid) other;
        return self.getId() != null && self.getId().equals(that.getId());
    }

    public static int entityHashCode(IdentifiableUuid self) {
        return self.getId() == null ? System.identityHashCode(self) : self.getId().hashCode();
    }
}
