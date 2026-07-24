-- K-21 + organization/domain refactor.
--
-- 1. email_domain column + its V2 partial index are dropped (redundant — subdomain is
--    the single source of truth; an organization may now own N email domains, tracked
--    in t_organization_domains). t_companies keeps its name as a legacy artefact; the
--    semantic unit is "organization".
-- 2. t_organization_domains — 1:N org-owned email domains (optional, verified=false by
--    default; custom-domain verification flow arrives in a later phase). Drives the
--    self-register allow-list (Epic 2.9) and LDAP/SSO wiring (enterprise phase).
-- 3. t_tenant_verification_tokens — K-21 two-phase signup. PROVISIONING Company holds
--    a token consumed by verifyAndProvision() to promote to ACTIVE (senkron schema
--    CREATE + Flyway + admin user). Soft-delete-less (GeneratedIdAuditEntity pattern);
--    invalidation is via used_at, not is_deleted.

-- (1) Drop email_domain column + its partial index.
DROP INDEX IF EXISTS uk_companies_email_domain;
ALTER TABLE t_companies DROP COLUMN IF EXISTS email_domain;

-- (2) Organization-owned email domains (1:N). SoftDeleteAuditEntity pattern → partial
--     unique index keeps a deleted domain reusable.
CREATE TABLE t_organization_domains (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    domain VARCHAR(150) NOT NULL,
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    verification_method VARCHAR(50),
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    version BIGINT,
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

-- (3) Tenant signup verification tokens (K-21). GeneratedIdAuditEntity pattern → no
--     is_deleted/version; lifecycle controlled by used_at + expires_at. Carries the
--     admin credentials captured at phase 1 so phase 2 (verifyAndProvision) can create
--     the admin user without re-prompting the user — the password is stored pre-hashed.
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
