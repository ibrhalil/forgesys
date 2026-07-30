-- Faz 3 (Stage 2): Task module — tasks belong to a project (TASKS project type).
-- Plain UUID columns for project_id/assignee_id (no JPA relations); validity is
-- enforced by FK constraints here + service-level existence checks.
-- No uniqueness constraint (task titles may repeat). Soft-delete + optimistic lock
-- + auditing columns mirror the other tenant tables.

CREATE TABLE t_tasks (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(2000),
    status VARCHAR(20) NOT NULL,
    priority VARCHAR(20) NOT NULL,
    assignee_id UUID,
    due_date DATE,
    is_deleted BOOLEAN NOT NULL DEFAULT false,
    deleted_at TIMESTAMP WITH TIME ZONE,
    version BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    CONSTRAINT fk_tasks_project FOREIGN KEY (project_id) REFERENCES t_projects(id) ON DELETE CASCADE,
    CONSTRAINT fk_tasks_assignee FOREIGN KEY (assignee_id) REFERENCES t_users(id)
);

CREATE INDEX idx_tasks_project ON t_tasks(project_id);
CREATE INDEX idx_tasks_assignee ON t_tasks(assignee_id);
