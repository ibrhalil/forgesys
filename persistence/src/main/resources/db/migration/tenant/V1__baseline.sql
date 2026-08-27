-- Consolidated baseline: Core IAM + Audit + Projects & Tasks
-- Merged from: V1__iam_users.sql, V1.1__iam_rbac.sql, V1.2__audit.sql, V1.3__pm_projects_tasks.sql

-- ==================== IAM USERS ====================
CREATE TABLE t_users (
    id UUID PRIMARY KEY,
    username VARCHAR(70) NOT NULL,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(150) NOT NULL,
    email_verified BOOLEAN NOT NULL DEFAULT false,
    is_deleted BOOLEAN NOT NULL DEFAULT false,
    deleted_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(100),
    updated_by VARCHAR(100)
);
CREATE INDEX idx_user_username ON t_users(username);
CREATE INDEX idx_user_email ON t_users(email);
CREATE UNIQUE INDEX uk_users_username ON t_users(username) WHERE is_deleted = false;
CREATE UNIQUE INDEX uk_users_email ON t_users(email) WHERE is_deleted = false;

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
    version BIGINT NOT NULL DEFAULT 0,
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
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    CONSTRAINT fk_user_profiles_user FOREIGN KEY (user_id) REFERENCES t_users(id) ON DELETE CASCADE
);

-- ==================== IAM RBAC ====================
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

-- ==================== AUDIT ====================
CREATE TABLE t_audit_logs (
    id UUID PRIMARY KEY,
    actor_id UUID,
    actor_name VARCHAR(200) NOT NULL,
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id UUID,
    entity_name VARCHAR(200),
    old_value JSONB,
    new_value JSONB,
    request_body JSONB,
    ip_address VARCHAR(45),
    trace_id VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(100),
    updated_by VARCHAR(100)
);
CREATE INDEX idx_audit_logs_actor_id ON t_audit_logs(actor_id);
CREATE INDEX idx_audit_logs_entity ON t_audit_logs(entity_type, entity_id);
CREATE INDEX idx_audit_logs_action ON t_audit_logs(action);
CREATE INDEX idx_audit_logs_created_at ON t_audit_logs(created_at);

CREATE TABLE t_login_history (
    id UUID PRIMARY KEY,
    user_id UUID,
    username VARCHAR(150) NOT NULL,
    success BOOLEAN NOT NULL,
    reason VARCHAR(50),
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(100),
    updated_by VARCHAR(100)
);
CREATE INDEX idx_login_history_user_id ON t_login_history(user_id);
CREATE INDEX idx_login_history_created_at ON t_login_history(created_at);
CREATE INDEX idx_login_history_success ON t_login_history(success);

-- Append-only triggers for audit tables
CREATE OR REPLACE FUNCTION prevent_audit_modification() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit tables are append-only: % not permitted on %', TG_OP, TG_TABLE_NAME
        USING ERRCODE = 'check_violation';
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER tr_audit_logs_immutable
    BEFORE UPDATE OR DELETE ON t_audit_logs
    FOR EACH ROW EXECUTE FUNCTION prevent_audit_modification();
CREATE TRIGGER tr_login_history_immutable
    BEFORE UPDATE OR DELETE ON t_login_history
    FOR EACH ROW EXECUTE FUNCTION prevent_audit_modification();

-- ==================== PROJECTS & TASKS ====================
CREATE TABLE t_projects (
    id UUID PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    description VARCHAR(500),
    project_type VARCHAR(30) NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT false,
    deleted_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(100),
    updated_by VARCHAR(100)
);
CREATE INDEX idx_project_name ON t_projects(name);
CREATE UNIQUE INDEX uk_projects_name ON t_projects(name) WHERE is_deleted = false;

CREATE TABLE t_tasks (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(2000),
    status VARCHAR(20) NOT NULL,
    priority VARCHAR(20) NOT NULL,
    assignee_id UUID,
    due_date DATE,
    is_deleted BOOLEAN NOT NULL DEFAULT false,
    deleted_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    CONSTRAINT fk_tasks_project FOREIGN KEY (project_id) REFERENCES t_projects(id) ON DELETE CASCADE,
    CONSTRAINT fk_tasks_assignee FOREIGN KEY (assignee_id) REFERENCES t_users(id)
);
CREATE INDEX idx_tasks_project ON t_tasks(project_id);
CREATE INDEX idx_tasks_assignee ON t_tasks(assignee_id);