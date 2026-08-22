import { api, normalizePage, toQuery } from '../../lib/api';
import type { PageParams, PageResponse } from '../../types';
import type { Project, ProjectRequest, Task, TaskRequest } from './types';

export const projectsApi = {
  list: (params: PageParams = {}) =>
    api.get<PageResponse<Project>>(`/api/v1/projects${toQuery(params)}`).then(normalizePage),
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
