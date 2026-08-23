-- Notes module (K-44 / Epic 3.2) — module-scoped tenant tables.
-- Runs via TenantMigrationSupport.migrateModule against the per-module history table
-- flyway_schema_history_mod_notes (baseline 0), OUTSIDE db/migration/tenant (K-16:
-- recursive location scan would swallow module versions into the core history).
-- PostgreSQL-only (partial unique indexes); never executes on H2.

CREATE TABLE t_note_categories (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    color VARCHAR(20),
    is_deleted BOOLEAN NOT NULL DEFAULT false,
    deleted_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(100),
    updated_by VARCHAR(100)
);
CREATE INDEX idx_note_categories_name ON t_note_categories(name);
CREATE UNIQUE INDEX uk_note_categories_name ON t_note_categories(name) WHERE is_deleted = false;

CREATE TABLE t_notes (
    id UUID PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    category_id UUID,
    pinned BOOLEAN NOT NULL DEFAULT false,
    is_deleted BOOLEAN NOT NULL DEFAULT false,
    deleted_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    -- Deleting a category keeps its notes (they become uncategorized); note titles
    -- may repeat, so no uniqueness constraint (task-title convention).
    CONSTRAINT fk_notes_category
        FOREIGN KEY (category_id) REFERENCES t_note_categories(id) ON DELETE SET NULL
);
CREATE INDEX idx_notes_category ON t_notes(category_id);
CREATE INDEX idx_notes_title ON t_notes(title);
