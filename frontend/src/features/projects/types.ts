// Projects & Tasks (pm:* — typed project containers, K-45)
export type ProjectType = 'TASKS' | 'NOTES' | 'APPS';

export interface Project {
  id: string;
  name: string;
  description: string | null;
  type: ProjectType;
  parentProjectId: string | null;
  isDefault: boolean;
}

/** Create + update share this shape (backend ProjectRequest). */
export interface ProjectRequest {
  name: string;
  description?: string;
  type: ProjectType;
  parentProjectId?: string | null;
}

/**
 * One entry of the creatable type catalog (GET /projects/types): the type, the module
 * supplying its content, and the per-type default container id (top-nav fallback).
 * The list derives from the tenant's ACTIVE modules — a disabled module's type never
 * appears here.
 */
export interface ProjectTypeInfo {
  type: ProjectType;
  moduleKey: string;
  defaultProjectId: string | null;
}

export type TaskStatus = 'TODO' | 'IN_PROGRESS' | 'DONE';
export type TaskPriority = 'LOW' | 'MEDIUM' | 'HIGH';

export interface Task {
  id: string;
  projectId: string;
  title: string;
  description: string | null;
  status: TaskStatus;
  priority: TaskPriority;
  assigneeId: string | null;
  /** ISO date yyyy-mm-dd. */
  dueDate: string | null;
}

export interface TaskRequest {
  title: string;
  description?: string;
  status?: TaskStatus;
  priority?: TaskPriority;
  assigneeId?: string | null;
  dueDate?: string | null;
}
