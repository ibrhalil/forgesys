package com.ibrhalil.forgesys.security;

import com.ibrhalil.forgesys.entity.Ownable;
import com.ibrhalil.forgesys.exception.BusinessException;
import com.ibrhalil.forgesys.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Faz 4b ownership/ABAC check. Use in the service layer to narrow a coarse permission
 * (e.g. {@code notes:note:delete}) to <em>the caller's own</em> record:
 *
 * <pre>{@code
 * Note note = noteRepository.findByIdOrThrow(id);
 * if (!principal.hasAuthority("iam:*-override")) {   // an admin bypass, if applicable
 *     ownershipGuard.assertOwner(note, principal.getUserId());
 * }
 * noteRepository.delete(note);
 * }</pre>
 *
 * <p>The guard throws {@link ErrorCode#AUTH_ACCESS_DENIED} (403) when the record is
 * unowned or owned by a different user. It is intentionally a plain method (not a Spring
 * Security {@code PermissionEvaluator}/SpEL): the caller composes it with whatever
 * permission/admin-override logic applies, keeping the policy in the service.
 */
@Component
public class OwnershipGuard {

    /**
     * Asserts {@code entity} is owned by {@code principalId}; throws
     * {@link ErrorCode#AUTH_ACCESS_DENIED} otherwise.
     */
    public void assertOwner(Ownable entity, UUID principalId) {
        if (entity == null) {
            throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED, "Resource not found");
        }
        UUID owner = entity.getOwnerId();
        if (owner == null || principalId == null || !owner.equals(principalId)) {
            throw new BusinessException(ErrorCode.AUTH_ACCESS_DENIED, "You may only access your own resources");
        }
    }
}
