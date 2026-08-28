import { api, normalizePage, searchQueryGet, toQuery } from '../../lib/api';
import type { FilterCriteria, PageParams, PageResponse } from '../../types';
import type { Project, ProjectRequest, ProjectType, ProjectTypeInfo, Task, TaskRequest } from './types';

export interface ProjectListParams extends PageParams {
  parentProjectId?: string;
  type?: ProjectType;
  /** K-49 structured column-filter clauses. */
  filters?: FilterCriteria[];
}

export const projectsApi = {
  list: (params: ProjectListParams = {}) => {
    // toQuery only carries page/size/sort(s)/q — thread the project-specific filters
    // on top of it (the notes buildQuery convention).
    const { parentProjectId, type, ...page } = params;
    const sp = new URLSearchParams(toQuery(page).replace(/^\?/, ''));
    if (parentProjectId) sp.set('parentProjectId', parentProjectId);
    if (type) sp.set('type', type);
    const qs = sp.toString();
    return api
      .get<PageResponse<Project>>(`/api/v1/projects${qs ? `?${qs}` : ''}`)
      .then(normalizePage);
  },
  /** K-55 wire-flip: one GET with the encoded `sq` query (scoped params fold as EQ; over-cap → POST fallback). */
  searchOrList: (params: ProjectListParams = {}) => searchQueryGet<Project>('/api/v1/projects', params),
  /** Creatable type catalog — derived from the tenant's ACTIVE modules (K-45). */
  types: () => api.get<ProjectTypeInfo[]>('/api/v1/projects/types'),
  get: (id: string) => api.get<Project>(`/api/v1/projects/${id}`),
  create: (data: ProjectRequest) => api.post<Project>('/api/v1/projects', data),
  update: (id: string, data: ProjectRequest) => api.put<Project>(`/api/v1/projects/${id}`, data),
  delete: (id: string) => api.delete<void>(`/api/v1/projects/${id}`),
};

// Tasks are nested under their owning project (scoped REST).
export const tasksApi = {
  /**
   * Paged tasks (K-37). The board fetches one large page and groups client-side
   * (backend hard cap: 1000).
   */
  list: (projectId: string) =>
    api
      .get<PageResponse<Task>>(
        `/api/v1/projects/${projectId}/tasks${toQuery({ size: 1000, sort: 'createdDate,desc' })}`,
      )
      .then(normalizePage),
  get: (projectId: string, taskId: string) =>
    api.get<Task>(`/api/v1/projects/${projectId}/tasks/${taskId}`),
  create: (projectId: string, data: TaskRequest) =>
    api.post<Task>(`/api/v1/projects/${projectId}/tasks`, data),
  update: (projectId: string, taskId: string, data: TaskRequest) =>
    api.put<Task>(`/api/v1/projects/${projectId}/tasks/${taskId}`, data),
  delete: (projectId: string, taskId: string) =>
    api.delete<void>(`/api/v1/projects/${projectId}/tasks/${taskId}`),
};
