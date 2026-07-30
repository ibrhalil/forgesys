-- Unified "all permissions" flag. A role with all_permissions = true implicitly holds
-- EVERY permission in the tenant (resolved dynamically at login/refresh by
-- CustomUserDetailsService.resolvePermissionNames), regardless of its explicit
-- t_role_permissions rows. This serves two purposes:
--   1. The built-in Admin role: RbacSeeder sets this flag true so admins always carry
--      all permissions without the seeder having to re-sync every catalog entry (and
--      without runtime-created permissions silently missing admins).
--   2. User-defined "ALL" roles: an admin can mark any role all-permissions via the
--      permissions UI instead of selecting every permission by hand.
--
-- Detection happens AFTER the parent-role closure in resolution, so a role that
-- (transitively) inherits from an all-permissions role is itself treated as all-
-- permissions. Explicit t_role_permissions rows on a flagged role are redundant for
-- authority resolution (the full set is a superset) and are kept out of the Admin role
-- by the seeder so that deleting a catalog permission is not blocked as "in use".

ALTER TABLE t_roles ADD COLUMN all_permissions BOOLEAN NOT NULL DEFAULT FALSE;
