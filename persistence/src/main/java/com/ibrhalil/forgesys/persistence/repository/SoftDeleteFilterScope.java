package com.ibrhalil.forgesys.persistence.repository;

import com.ibrhalil.forgesys.entity.SoftDeleteAuditEntity;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;

import java.util.Collection;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Scoped "include deleted" read window (EGH item 4): disables the auto-enabled
 * soft-delete filter for the current session/transaction, runs the action, then
 * re-enables it. Results are DETACHED before the window closes — otherwise the
 * opt-in load leaves the deleted rows in the persistence context, where later
 * default reads in the same transaction would surface them from the L1 cache
 * (bypassing both the filter and the former @SQLRestriction semantics).
 */
public final class SoftDeleteFilterScope {

    private SoftDeleteFilterScope() {
    }

    public static <T> T includingDeleted(EntityManager entityManager, Supplier<T> action) {
        Session session = entityManager.unwrap(Session.class);
        session.disableFilter(SoftDeleteAuditEntity.SOFT_DELETE_FILTER);
        try {
            T result = action.get();
            detach(entityManager, result);
            return result;
        } finally {
            session.enableFilter(SoftDeleteAuditEntity.SOFT_DELETE_FILTER);
        }
    }

    private static void detach(EntityManager entityManager, Object result) {
        if (result instanceof Optional<?> optional) {
            optional.ifPresent(entityManager::detach);
        } else if (result instanceof Collection<?> collection) {
            collection.forEach(entityManager::detach);
        } else if (result != null) {
            entityManager.detach(result);
        }
    }
}
