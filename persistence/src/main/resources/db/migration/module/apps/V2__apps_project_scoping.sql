-- K-45 step 5: apps become project-scoped (typed container) — the mirror of
-- module/notes/V2 for the app COLLECTION container type.
-- Ordering guarantee: tenant/V3 (parent_project_id + is_default) has already run --
-- TenantMigrationRunner (@Order(2)) applies the core tree before ModuleSyncRunner
-- re-syncs module trees, so t_projects.is_default is safe to reference here.
ALTER TABLE t_apps ADD COLUMN project_id UUID;

-- Default "Genel" APPS container: adopt an existing same-named project when one is
-- present (ModuleActivationService.ensureDefaultProjectInNewTx mirrors this), else
-- create one.
UPDATE t_projects SET is_default = true
WHERE id = (
    SELECT id FROM t_projects
    WHERE project_type = 'APPS' AND is_deleted = false AND name = 'Genel'
    ORDER BY created_at
    LIMIT 1
)
AND NOT EXISTS (
    SELECT 1 FROM t_projects
    WHERE project_type = 'APPS' AND is_default = true AND is_deleted = false
);

INSERT INTO t_projects (id, name, project_type, is_default, created_at, updated_at, created_by, updated_by)
SELECT gen_random_uuid(), 'Genel', 'APPS', true, now(), now(), 'system', 'system'
WHERE NOT EXISTS (
    SELECT 1 FROM t_projects
    WHERE project_type = 'APPS' AND is_default = true AND is_deleted = false
);

-- Strict model: every existing app joins the default APPS container, then the column
-- is locked NOT NULL. App children (properties/records/views) cascade through the app.
UPDATE t_apps
SET project_id = (SELECT id FROM t_projects
                  WHERE project_type = 'APPS' AND is_default = true AND is_deleted = false)
WHERE project_id IS NULL;

ALTER TABLE t_apps ALTER COLUMN project_id SET NOT NULL;

ALTER TABLE t_apps ADD CONSTRAINT fk_apps_project
    FOREIGN KEY (project_id) REFERENCES t_projects(id) ON DELETE CASCADE;
CREATE INDEX idx_apps_project ON t_apps(project_id);
