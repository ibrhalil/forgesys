-- Faz 3 (Stage 1): Project module — tenant-scoped project/workspace container.
-- A project's project_type decides which built-in modules are surfaced inside it.
-- Soft-delete + optimistic-lock + auditing columns mirror the other tenant tables.
-- Name uniqueness is a PARTIAL index (WHERE is_deleted = false) so a soft-deleted
-- project's name can be reused by a fresh project ([RISK-17]).

CREATE TABLE t_projects (
    id UUID PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    description VARCHAR(500),
    project_type VARCHAR(30) NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT false,
    deleted_at TIMESTAMP WITH TIME ZONE,
    version BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(100),
    updated_by VARCHAR(100)
);

CREATE INDEX idx_project_name ON t_projects(name);
CREATE UNIQUE INDEX uk_projects_name ON t_projects(name) WHERE is_deleted = false;
