-- EGH D2 remediation (one-time, versioned replacement of the runtime RbacSeeder purge):
-- retired platform:* permissions (pre-K-50 tenants) leave the tenant schema for good.
-- Grants must fall BEFORE the permission rows (FK t_role_permissions_permission).
-- No is_deleted filter on purpose: the old bulk JPQL purge also ignored soft-deleted
-- rows, and a soft-deleted platform:* permission is equally retired.
DELETE FROM t_role_permissions rp
USING t_permissions p
WHERE rp.permission_id = p.id
  AND p.name LIKE 'platform:%';

DELETE FROM t_permissions
WHERE name LIKE 'platform:%';
