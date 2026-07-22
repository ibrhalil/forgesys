ALTER TABLE t_users DROP CONSTRAINT IF EXISTS uk_users_username;
ALTER TABLE t_users DROP CONSTRAINT IF EXISTS uk_users_email;
ALTER TABLE t_roles DROP CONSTRAINT IF EXISTS uk_roles_name;
ALTER TABLE t_permissions DROP CONSTRAINT IF EXISTS uk_permissions_name;
ALTER TABLE t_groups DROP CONSTRAINT IF EXISTS uk_groups_name;

CREATE UNIQUE INDEX uk_users_username ON t_users(username) WHERE is_deleted = false;
CREATE UNIQUE INDEX uk_users_email ON t_users(email) WHERE is_deleted = false;
CREATE UNIQUE INDEX uk_roles_name ON t_roles(name) WHERE is_deleted = false;
CREATE UNIQUE INDEX uk_permissions_name ON t_permissions(name) WHERE is_deleted = false;
CREATE UNIQUE INDEX uk_groups_name ON t_groups(name) WHERE is_deleted = false;
