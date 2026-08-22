CREATE TABLE t_companies (
    id UUID PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    subdomain VARCHAR(100) NOT NULL,
    schema_name VARCHAR(100) NOT NULL,
    db_role VARCHAR(100),
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
CREATE TABLE t_organization_domains (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    domain VARCHAR(150) NOT NULL,
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    verification_method VARCHAR(50),
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    CONSTRAINT fk_organization_domains_company
        FOREIGN KEY (company_id) REFERENCES t_companies(id),
    CONSTRAINT ck_org_domains_method
        CHECK (verification_method IS NULL OR verification_method IN ('DNS_TXT', 'MX'))
);
CREATE INDEX idx_organization_domains_company ON t_organization_domains(company_id);
CREATE INDEX idx_organization_domains_domain ON t_organization_domains(domain);
CREATE UNIQUE INDEX uk_organization_domains_domain
    ON t_organization_domains(domain) WHERE is_deleted = FALSE;
