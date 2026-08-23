import { api, normalizePage, toQuery } from '../../lib/api';
import type { PageParams, PageResponse } from '../../types';
import type { Note, NoteCategory, NoteCategoryRequest, NoteRequest } from './types';

export interface NoteListParams extends PageParams {
  categoryId?: string;
  pinned?: boolean;
  /** Cross-container narrowing (flat list) — the nested panel also lands here. */
  projectId?: string;
}

export const notesApi = {
  list: (params: NoteListParams = {}) => {
    // toQuery only carries page/size/sort(s)/q — thread the note-specific filters
    // on top of it (the audit buildQuery convention).
    const { categoryId, pinned, projectId, ...page } = params;
    const sp = new URLSearchParams(toQuery(page).replace(/^\?/, ''));
    if (categoryId) sp.set('categoryId', categoryId);
    if (pinned != null) sp.set('pinned', String(pinned));
    if (projectId) sp.set('projectId', projectId);
    const qs = sp.toString();
    return api
      .get<PageResponse<Note>>(`/api/v1/notes${qs ? `?${qs}` : ''}`)
      .then(normalizePage);
  },
  get: (id: string) => api.get<Note>(`/api/v1/notes/${id}`),
  create: (data: NoteRequest) => api.post<Note>('/api/v1/notes', data),
  update: (id: string, data: NoteRequest) => api.put<Note>(`/api/v1/notes/${id}`, data),
  delete: (id: string) => api.delete<void>(`/api/v1/notes/${id}`),
};

export interface NoteCategoryListParams extends PageParams {
  projectId?: string;
}

export const noteCategoriesApi = {
  list: (params: NoteCategoryListParams = {}) => {
    const { projectId, ...page } = params;
    const sp = new URLSearchParams(toQuery(page).replace(/^\?/, ''));
    if (projectId) sp.set('projectId', projectId);
    const qs = sp.toString();
    return api
      .get<PageResponse<NoteCategory>>(`/api/v1/note-categories${qs ? `?${qs}` : ''}`)
      .then(normalizePage);
  },
  create: (data: NoteCategoryRequest) => api.post<NoteCategory>('/api/v1/note-categories', data),
  update: (id: string, data: NoteCategoryRequest) =>
    api.put<NoteCategory>(`/api/v1/note-categories/${id}`, data),
  delete: (id: string) => api.delete<void>(`/api/v1/note-categories/${id}`),
};
