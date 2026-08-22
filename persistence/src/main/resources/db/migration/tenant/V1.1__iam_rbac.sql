CREATE TABLE t_roles (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    all_permissions BOOLEAN NOT NULL DEFAULT FALSE,
    is_deleted BOOLEAN NOT NULL DEFAULT false,
    deleted_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(100),
    updated_by VARCHAR(100)
);
CREATE INDEX idx_role_name ON t_roles(name);
CREATE UNIQUE INDEX uk_roles_name ON t_roles(name) WHERE is_deleted = false;
CREATE TABLE t_permissions (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    is_deleted BOOLEAN NOT NULL DEFAULT false,
    deleted_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(100),
    updated_by VARCHAR(100)
);
CREATE INDEX idx_permission_name ON t_permissions(name);
CREATE UNIQUE INDEX uk_permissions_name ON t_permissions(name) WHERE is_deleted = false;
CREATE TABLE t_groups (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT true,
    is_deleted BOOLEAN NOT NULL DEFAULT false,
    deleted_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(100),
    updated_by VARCHAR(100)
);
CREATE UNIQUE INDEX uk_groups_name ON t_groups(name) WHERE is_deleted = false;
CREATE TABLE t_role_permissions (
    role_id UUID NOT NULL,
    permission_id UUID NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES t_roles(id) ON DELETE CASCADE,
    CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_id) REFERENCES t_permissions(id) ON DELETE CASCADE,
    CONSTRAINT uk_role_permissions_role_permission UNIQUE (role_id, permission_id)
);
CREATE TABLE t_user_groups (
    user_id UUID NOT NULL,
    group_id UUID NOT NULL,
    PRIMARY KEY (user_id, group_id),
    CONSTRAINT fk_user_groups_user FOREIGN KEY (user_id) REFERENCES t_users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_groups_group FOREIGN KEY (group_id) REFERENCES t_groups(id) ON DELETE CASCADE,
    CONSTRAINT uk_user_groups_user_group UNIQUE (user_id, group_id)
);
CREATE TABLE t_user_roles (
    user_id UUID NOT NULL,
    role_id UUID NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES t_users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES t_roles(id) ON DELETE CASCADE,
    CONSTRAINT uk_user_roles_user_role UNIQUE (user_id, role_id)
);
CREATE TABLE t_group_roles (
    group_id UUID NOT NULL,
    role_id UUID NOT NULL,
    PRIMARY KEY (group_id, role_id),
    CONSTRAINT fk_group_roles_group FOREIGN KEY (group_id) REFERENCES t_groups(id) ON DELETE CASCADE,
    CONSTRAINT fk_group_roles_role FOREIGN KEY (role_id) REFERENCES t_roles(id) ON DELETE CASCADE,
    CONSTRAINT uk_group_roles_group_role UNIQUE (group_id, role_id)
);
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
CREATE INDEX idx_role_parents_parent ON t_role_parents(parent_role_id);
