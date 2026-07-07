CREATE TABLE t_companies (
    id UUID PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    subdomain VARCHAR(100) NOT NULL,
    email_domain VARCHAR(150) NOT NULL,
    schema_name VARCHAR(100) NOT NULL,
    db_role VARCHAR(100),
    status VARCHAR(20) NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT false,
    deleted_at TIMESTAMP WITH TIME ZONE,
    version BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    CONSTRAINT uk_companies_name UNIQUE (name),
    CONSTRAINT uk_companies_subdomain UNIQUE (subdomain),
    CONSTRAINT uk_companies_email_domain UNIQUE (email_domain),
    CONSTRAINT uk_companies_schema_name UNIQUE (schema_name)
);

CREATE INDEX idx_company_subdomain ON t_companies(subdomain);
CREATE INDEX idx_company_email_domain ON t_companies(email_domain);
