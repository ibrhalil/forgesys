CREATE TABLE t_tenant_verification_tokens (
    id UUID PRIMARY KEY,
    token VARCHAR(255) NOT NULL,
    company_id UUID NOT NULL,
    admin_email VARCHAR(150) NOT NULL,
    admin_password_hash VARCHAR(255) NOT NULL,
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
