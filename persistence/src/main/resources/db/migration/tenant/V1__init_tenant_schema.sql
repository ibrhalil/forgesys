CREATE TABLE t_users (
    id UUID PRIMARY KEY,
    username VARCHAR(70) NOT NULL,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(150) NOT NULL,
    email_verified BOOLEAN NOT NULL DEFAULT false,
    email_verification_token VARCHAR(512),
    email_verification_token_expires_at TIMESTAMP WITH TIME ZONE,
    password_reset_token VARCHAR(512),
    password_reset_token_expires_at TIMESTAMP WITH TIME ZONE,
    is_deleted BOOLEAN NOT NULL DEFAULT false,
    deleted_at TIMESTAMP WITH TIME ZONE,
    version BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE INDEX idx_user_username ON t_users(username);
CREATE INDEX idx_user_email ON t_users(email);

CREATE TABLE t_user_accounts (
    user_id UUID PRIMARY KEY,
    enabled BOOLEAN NOT NULL DEFAULT true,
    account_non_expired BOOLEAN NOT NULL DEFAULT true,
    account_non_locked BOOLEAN NOT NULL DEFAULT true,
    credentials_non_expired BOOLEAN NOT NULL DEFAULT true,
    failed_login_attempts INT NOT NULL DEFAULT 0,
    locked_until TIMESTAMP WITH TIME ZONE,
    last_login_at TIMESTAMP WITH TIME ZONE,
    token_invalid_before TIMESTAMP WITH TIME ZONE,
    is_deleted BOOLEAN NOT NULL DEFAULT false,
    deleted_at TIMESTAMP WITH TIME ZONE,
    version BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    CONSTRAINT fk_user_accounts_user FOREIGN KEY (user_id) REFERENCES t_users(id) ON DELETE CASCADE
);

CREATE TABLE t_user_profiles (
    user_id UUID PRIMARY KEY,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    phone VARCHAR(50),
    address VARCHAR(255),
    city VARCHAR(100),
    country VARCHAR(100),
    zip VARCHAR(20),
    is_deleted BOOLEAN NOT NULL DEFAULT false,
    deleted_at TIMESTAMP WITH TIME ZONE,
    version BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    CONSTRAINT fk_user_profiles_user FOREIGN KEY (user_id) REFERENCES t_users(id) ON DELETE CASCADE
);

CREATE TABLE t_roles (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    is_deleted BOOLEAN NOT NULL DEFAULT false,
    deleted_at TIMESTAMP WITH TIME ZONE,
    version BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    CONSTRAINT uk_roles_name UNIQUE (name)
);

CREATE INDEX idx_role_name ON t_roles(name);

CREATE TABLE t_permissions (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    is_deleted BOOLEAN NOT NULL DEFAULT false,
    deleted_at TIMESTAMP WITH TIME ZONE,
    version BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    CONSTRAINT uk_permissions_name UNIQUE (name)
);

CREATE INDEX idx_permission_name ON t_permissions(name);

CREATE TABLE t_role_permissions (
    role_id UUID NOT NULL,
    permission_id UUID NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES t_roles(id) ON DELETE CASCADE,
    CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_id) REFERENCES t_permissions(id) ON DELETE CASCADE,
    CONSTRAINT uk_role_permissions_role_permission UNIQUE (role_id, permission_id)
);

CREATE TABLE t_groups (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT true,
    is_deleted BOOLEAN NOT NULL DEFAULT false,
    deleted_at TIMESTAMP WITH TIME ZONE,
    version BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    CONSTRAINT uk_groups_name UNIQUE (name)
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

CREATE TABLE t_refresh_tokens (
    id UUID PRIMARY KEY,
    token VARCHAR(512) NOT NULL,
    user_id UUID NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES t_users(id) ON DELETE CASCADE,
    CONSTRAINT uk_refresh_tokens_token UNIQUE (token)
);

CREATE INDEX idx_refresh_token_token ON t_refresh_tokens(token);
