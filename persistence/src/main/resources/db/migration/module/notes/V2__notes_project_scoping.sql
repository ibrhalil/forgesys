-- K-45 step 4: notes become project-scoped (typed container).
-- Ordering guarantee: tenant/V3 (parent_project_id + is_default) has already run --
-- TenantMigrationRunner (@Order(2)) applies the core tree before ModuleSyncRunner
-- re-syncs module trees, so t_projects.is_default is safe to reference here.
ALTER TABLE t_note_categories ADD COLUMN project_id UUID;
ALTER TABLE t_notes ADD COLUMN project_id UUID;

-- Default "Genel" NOTES container: adopt an existing same-named project when one is
-- present (ModuleActivationService.ensureDefaultProjectInNewTx mirrors this for the
-- H2/activation path), else create one.
UPDATE t_projects SET is_default = true
WHERE id = (
    SELECT id FROM t_projects
    WHERE project_type = 'NOTES' AND is_deleted = false AND name = 'Genel'
    ORDER BY created_at
    LIMIT 1
)
AND NOT EXISTS (
    SELECT 1 FROM t_projects
    WHERE project_type = 'NOTES' AND is_default = true AND is_deleted = false
);

INSERT INTO t_projects (id, name, project_type, is_default, created_at, updated_at, created_by, updated_by)
SELECT gen_random_uuid(), 'Genel', 'NOTES', true, now(), now(), 'system', 'system'
WHERE NOT EXISTS (
    SELECT 1 FROM t_projects
    WHERE project_type = 'NOTES' AND is_default = true AND is_deleted = false
);

-- Strict model: every existing category/note joins the default container, then the
-- column is locked NOT NULL.
UPDATE t_note_categories
SET project_id = (SELECT id FROM t_projects
                  WHERE project_type = 'NOTES' AND is_default = true AND is_deleted = false)
WHERE project_id IS NULL;
UPDATE t_notes
SET project_id = (SELECT id FROM t_projects
                  WHERE project_type = 'NOTES' AND is_default = true AND is_deleted = false)
WHERE project_id IS NULL;

ALTER TABLE t_note_categories ALTER COLUMN project_id SET NOT NULL;
ALTER TABLE t_notes ALTER COLUMN project_id SET NOT NULL;

ALTER TABLE t_note_categories ADD CONSTRAINT fk_note_categories_project
    FOREIGN KEY (project_id) REFERENCES t_projects(id) ON DELETE CASCADE;
ALTER TABLE t_notes ADD CONSTRAINT fk_notes_project
    FOREIGN KEY (project_id) REFERENCES t_projects(id) ON DELETE CASCADE;
CREATE INDEX idx_note_categories_project ON t_note_categories(project_id);
CREATE INDEX idx_notes_project ON t_notes(project_id);
