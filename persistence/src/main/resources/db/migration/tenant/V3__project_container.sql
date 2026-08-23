-- K-45 typed project container: optional nesting + the per-type default marker.
-- Column-only migration (no data backfill): the "Genel" default containers belong to
-- their modules (module/notes/V2, module/apps/V2) and are ensured on module activation,
-- so a tenant that never activated a module never gets its default container.
ALTER TABLE t_projects ADD COLUMN parent_project_id UUID;
ALTER TABLE t_projects ADD COLUMN is_default BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE t_projects ADD CONSTRAINT fk_projects_parent
    FOREIGN KEY (parent_project_id) REFERENCES t_projects(id) ON DELETE SET NULL;
CREATE INDEX idx_projects_parent ON t_projects(parent_project_id);
CREATE UNIQUE INDEX uk_projects_default_type ON t_projects(project_type)
    WHERE is_default = true AND is_deleted = false;
