// Projects & Tasks (pm:* — project-management module, Faz 3)
export type ProjectType = 'TASKS' | 'NOTES';

export interface Project {
  id: string;
  name: string;
  description: string | null;
  type: ProjectType;
}

/** Create + update share this shape (backend ProjectRequest). */
export interface ProjectRequest {
  name: string;
  description?: string;
  type: ProjectType;
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
