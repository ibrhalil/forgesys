import { api, normalizePage, searchPost, toQuery } from '../../lib/api';
import type { FilterCriteria, PageParams, PageResponse, SearchRequestBody } from '../../types';
import type { Note, NoteCategory, NoteCategoryRequest, NoteRequest } from './types';

export interface NoteListParams extends PageParams {
  categoryId?: string;
  pinned?: boolean;
  /** Cross-container narrowing (flat list) — the nested panel also lands here. */
  projectId?: string;
  /** K-49 structured column-filter clauses. */
  filters?: FilterCriteria[];
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
  /**
   * Engine list read: structured column-filter clauses route through
   * `POST /notes/search` (with the scoped toolbar params folded in as EQ clauses);
   * without clauses everything stays on the plain GET — the legacy toolbar filters
   * keep their bookmarkable query-param shape.
   */
  searchOrList: ({
    filters,
    categoryId,
    pinned,
    projectId,
    ...params
  }: NoteListParams = {}) => {
    if (!filters?.length) {
      return notesApi.list({ ...params, categoryId, pinned, projectId });
    }
    const clauses: FilterCriteria[] = [...filters];
    if (categoryId) clauses.push({ field: 'categoryId', operator: 'EQ', values: [categoryId] });
    if (pinned != null) clauses.push({ field: 'pinned', operator: 'EQ', values: [String(pinned)] });
    if (projectId) clauses.push({ field: 'projectId', operator: 'EQ', values: [projectId] });
    const body: SearchRequestBody = { ...params, filters: clauses };
    return searchPost<Note>('/api/v1/notes/search', body);
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
