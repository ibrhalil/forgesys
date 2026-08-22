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
