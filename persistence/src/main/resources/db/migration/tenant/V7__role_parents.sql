-- Faz 4a: role inheritance. A role inherits the permissions of its parent roles
-- (transitively), resolved by CustomUserDetailsService.resolveAuthorities with a
-- visited-set guard. Acyclicity is enforced in RoleService.setParents (no self-parent,
-- no path back to the child); the visited-set makes resolution cycle-safe regardless.
--
-- t_roles uses soft-delete (an UPDATE setting is_deleted), so ON DELETE CASCADE never
-- fires here; orphan rows after a soft-deleted parent are harmless because
-- @SQLRestriction filters soft-deleted roles out of the parent traversal.

CREATE TABLE t_role_parents (
    role_id UUID NOT NULL,
    parent_role_id UUID NOT NULL,
    PRIMARY KEY (role_id, parent_role_id),
    CONSTRAINT fk_role_parents_role FOREIGN KEY (role_id)
        REFERENCES t_roles(id) ON DELETE CASCADE,
    CONSTRAINT fk_role_parents_parent FOREIGN KEY (parent_role_id)
        REFERENCES t_roles(id) ON DELETE CASCADE,
    CONSTRAINT uk_role_parents_role_parent UNIQUE (role_id, parent_role_id)
);

-- Reverse lookup index: "who inherits from role X?" (revoke/audit fan-out).
CREATE INDEX idx_role_parents_parent ON t_role_parents(parent_role_id);
