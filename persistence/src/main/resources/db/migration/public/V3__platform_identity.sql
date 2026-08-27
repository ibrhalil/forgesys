-- K-50: global platform identities (superadmin + service accounts) live in the
-- public schema — tenant users stay in tenant schemas (user-per-tenant re-scoped
-- to tenant data). API keys store only the SHA-256 digest (TokenHasher pattern,
-- RISK-30); the raw key is shown exactly once at creation. Platform audit is
-- append-only like the tenant audit tables (K-19 trigger pattern).
CREATE TABLE t_platform_users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    user_type VARCHAR(10) NOT NULL,
    password_hash VARCHAR(255),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    locked_until TIMESTAMP WITH TIME ZONE,
    failed_attempts INT NOT NULL DEFAULT 0,
    token_invalid_before TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    CONSTRAINT uk_platform_users_email UNIQUE (email),
    CONSTRAINT ck_platform_users_type CHECK (user_type IN ('HUMAN', 'SERVICE'))
);
CREATE TABLE t_platform_api_keys (
    id UUID PRIMARY KEY,
    platform_user_id UUID NOT NULL,
    name VARCHAR(150) NOT NULL,
    key_prefix VARCHAR(20) NOT NULL,
    key_hash VARCHAR(255) NOT NULL,
    scopes TEXT NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE,
    last_used_at TIMESTAMP WITH TIME ZONE,
    revoked_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    CONSTRAINT uk_platform_api_keys_prefix UNIQUE (key_prefix),
    CONSTRAINT uk_platform_api_keys_hash UNIQUE (key_hash),
    CONSTRAINT fk_platform_api_keys_user
        FOREIGN KEY (platform_user_id) REFERENCES t_platform_users(id)
);
CREATE INDEX idx_platform_api_keys_user ON t_platform_api_keys(platform_user_id);
CREATE TABLE t_platform_audit_logs (
    id UUID PRIMARY KEY,
    actor_id UUID,
    actor_type VARCHAR(10) NOT NULL,
    action VARCHAR(100) NOT NULL,
    target_type VARCHAR(100),
    target_id UUID,
    detail TEXT,
    ip_address VARCHAR(45),
    trace_id VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(100),
    updated_by VARCHAR(100)
);
CREATE INDEX idx_platform_audit_logs_actor_id ON t_platform_audit_logs(actor_id);
CREATE INDEX idx_platform_audit_logs_action ON t_platform_audit_logs(action);
CREATE INDEX idx_platform_audit_logs_target ON t_platform_audit_logs(target_type, target_id);
CREATE INDEX idx_platform_audit_logs_created_at ON t_platform_audit_logs(created_at);
CREATE OR REPLACE FUNCTION prevent_platform_audit_modification() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'platform audit table is append-only: % not permitted on %', TG_OP, TG_TABLE_NAME
        USING ERRCODE = 'check_violation';
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER tr_platform_audit_logs_immutable
    BEFORE UPDATE OR DELETE ON t_platform_audit_logs
    FOR EACH ROW EXECUTE FUNCTION prevent_platform_audit_modification();
