import { api, normalizePage, toQuery } from '../../lib/api';
import type { PageParams, PageResponse } from '../../types';
import type { Note, NoteCategory, NoteCategoryRequest, NoteRequest } from './types';

export interface NoteListParams extends PageParams {
  categoryId?: string;
  pinned?: boolean;
}

export const notesApi = {
  list: (params: NoteListParams = {}) => {
    // toQuery only carries page/size/sort(s)/q — thread the note-specific filters
    // on top of it (the audit buildQuery convention).
    const { categoryId, pinned, ...page } = params;
    const sp = new URLSearchParams(toQuery(page).replace(/^\?/, ''));
    if (categoryId) sp.set('categoryId', categoryId);
    if (pinned != null) sp.set('pinned', String(pinned));
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

export const noteCategoriesApi = {
  list: () =>
    api
      .get<PageResponse<NoteCategory>>(`/api/v1/note-categories${toQuery({ size: 100, sort: 'name,asc' })}`)
      .then(normalizePage),
  create: (data: NoteCategoryRequest) => api.post<NoteCategory>('/api/v1/note-categories', data),
  update: (id: string, data: NoteCategoryRequest) =>
    api.put<NoteCategory>(`/api/v1/note-categories/${id}`, data),
  delete: (id: string) => api.delete<void>(`/api/v1/note-categories/${id}`),
};
