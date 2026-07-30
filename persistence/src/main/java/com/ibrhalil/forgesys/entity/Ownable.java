package com.ibrhalil.forgesys.entity;

import java.util.UUID;

/**
 * Faz 4b ownership/ABAC template. A tenant entity that exposes its owning user implements
 * this so the service layer can enforce <em>"a user may only act on their own record"</em>
 * on top of coarse role permissions (e.g. {@code notes:note:write} lets you write notes;
 * the ownership guard narrows that to <em>your</em> notes).
 *
 * <p>Implementations typically return an explicit {@code ownerId} field or the auditing
 * {@code createdBy}. Intended for the product modules (Notes / Warehouse / Logistics);
 * the built-in User/Role/Group/Permission entities do not implement it (they are
 * tenant-administered, not user-owned). The check itself lives in
 * {@code OwnershipGuard} (backend) — this interface only declares the contract so the
 * persistence layer (entities) can satisfy it without depending on the backend.
 */
public interface Ownable {

    /** The user id that owns this record (the "creator" / assignee). */
    UUID getOwnerId();
}
