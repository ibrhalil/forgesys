ALTER TABLE t_companies DROP CONSTRAINT IF EXISTS uk_companies_name;
ALTER TABLE t_companies DROP CONSTRAINT IF EXISTS uk_companies_subdomain;
ALTER TABLE t_companies DROP CONSTRAINT IF EXISTS uk_companies_email_domain;
ALTER TABLE t_companies DROP CONSTRAINT IF EXISTS uk_companies_schema_name;

CREATE UNIQUE INDEX uk_companies_name ON t_companies(name) WHERE is_deleted = false;
CREATE UNIQUE INDEX uk_companies_subdomain ON t_companies(subdomain) WHERE is_deleted = false;
CREATE UNIQUE INDEX uk_companies_email_domain ON t_companies(email_domain) WHERE is_deleted = false;
CREATE UNIQUE INDEX uk_companies_schema_name ON t_companies(schema_name) WHERE is_deleted = false;
