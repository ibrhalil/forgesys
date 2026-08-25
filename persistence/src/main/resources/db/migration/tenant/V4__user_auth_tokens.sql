-- User lifecycle tokens (email verification, password reset) — tenant schema.
-- [RISK-30 pattern] Only the SHA-256 digest of the raw token is stored; the raw value
-- lives solely in the mailed link. Single-use via used_at; superseded tokens are
-- stamped used_at at re-issue (only the newest link works).
CREATE TABLE t_auth_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    purpose VARCHAR(30) NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    CONSTRAINT uk_auth_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_auth_tokens_user FOREIGN KEY (user_id) REFERENCES t_users(id)
);
CREATE INDEX idx_auth_tokens_user_purpose ON t_auth_tokens(user_id, purpose);
