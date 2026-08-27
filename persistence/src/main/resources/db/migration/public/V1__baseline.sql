-- Consolidated baseline: t_companies + t_tenant_verification_tokens (RISK-30 hash-at-rest ready)
-- Merged from: V1__tenant_registry.sql, V1.1__signup_verification_tokens.sql, V3__token_hash_at_rest.sql

CREATE TABLE t_companies (
    id UUID PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    subdomain VARCHAR(100) NOT NULL,
    schema_name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT false,
    deleted_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(100),
    updated_by VARCHAR(100)
);
CREATE INDEX idx_company_subdomain ON t_companies(subdomain);
CREATE UNIQUE INDEX uk_companies_name ON t_companies(name) WHERE is_deleted = false;
CREATE UNIQUE INDEX uk_companies_subdomain ON t_companies(subdomain) WHERE is_deleted = false;
CREATE UNIQUE INDEX uk_companies_schema_name ON t_companies(schema_name) WHERE is_deleted = false;

CREATE TABLE t_tenant_verification_tokens (
    id UUID PRIMARY KEY,
    token VARCHAR(255) NOT NULL,
    company_id UUID NOT NULL,
    admin_email VARCHAR(150) NOT NULL,
    admin_password_hash VARCHAR(255),
    admin_first_name VARCHAR(100),
    admin_last_name VARCHAR(100),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    CONSTRAINT uk_tenant_verification_tokens_token UNIQUE (token),
    CONSTRAINT fk_tenant_verification_tokens_company
        FOREIGN KEY (company_id) REFERENCES t_companies(id)
);
CREATE INDEX idx_tenant_verification_tokens_company ON t_tenant_verification_tokens(company_id);