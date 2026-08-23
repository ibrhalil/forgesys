-- K-45 typed project container: optional nesting + the per-type default marker.
-- Column-only for existing data (no backfill): the "Genel" default containers belong
-- to their modules (module/notes/V2, module/apps/V2) and are ensured on module
-- activation, so a tenant that never activated a module never gets its default.
--
-- Name uniqueness becomes PER-TYPE: each type is its own namespace, so the notes and
-- apps modules can both ship a "Genel" container (same name, different types). Same
-- re-usable-name-across-types semantics as the module system itself.
ALTER TABLE t_projects ADD COLUMN parent_project_id UUID;
ALTER TABLE t_projects ADD COLUMN is_default BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE t_projects ADD CONSTRAINT fk_projects_parent
    FOREIGN KEY (parent_project_id) REFERENCES t_projects(id) ON DELETE SET NULL;
CREATE INDEX idx_projects_parent ON t_projects(parent_project_id);
DROP INDEX IF EXISTS uk_projects_name;
CREATE UNIQUE INDEX uk_projects_type_name ON t_projects(project_type, name)
    WHERE is_deleted = false;
CREATE UNIQUE INDEX uk_projects_default_type ON t_projects(project_type)
    WHERE is_default = true AND is_deleted = false;
