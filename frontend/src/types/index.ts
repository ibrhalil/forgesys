// Shared, cross-feature types only. Domain types live in their feature folder
// (features/<name>/types.ts): auth, users, roles, groups, permissions, projects, audit, sessions.

// ─── RBAC summaries ───
// Lightweight references embedded inside User/Group/Role responses
// (RoleSummary/GroupSummary/UserSummary on the backend — only id + name/email).
export interface RoleSummary {
  id: string;
  name: string;
}

export interface GroupSummary {
  id: string;
  name: string;
}

export interface UserSummary {
  id: string;
  email: string;
}

// ─── API error (mirrors backend ApiErrorResponse) ───
export interface ApiFieldError {
  field: string;
  rejectedValue: unknown;
  message: string;
}

export interface ApiErrorResponse {
  timestamp: string;
  status: number;
  error: string;
  code: string;
  message: string;
  path: string;
  traceId: string;
  fields: ApiFieldError[];
}

// ─── Pagination / sorting ───

export type SortDir = 'asc' | 'desc';

/** Single-column sort state for list pages (backend `sort=field,dir`). */
export interface SortState {
  field: string;
  dir: SortDir;
}

export interface PageParams {
  page?: number;
  size?: number;
  /** Raw Spring Data sort string, e.g. "createdDate,desc" (audit pages). */
  sort?: string;
  /** Structured multi-sort, serialized as repeated `sort=field,dir` params. */
  sorts?: SortState[];
  /** Server-side global search (OR-CONTAINS over the feature's searchable fields). */
  q?: string;
}

/**
 * Raw paginated list response. Current backend shape is the API-owned
 * `PageResponse` (`data[] + meta`); the legacy Spring Data fields stay as a
 * tolerance so a mixed backend during rollout keeps working. See `normalizePage`.
 */
export interface PageResponse<T> {
  data?: T[];
  meta?: {
    page: number;
    pageSize: number;
    totalElements: number;
    totalPages: number;
    hasNext: boolean;
    hasPrevious: boolean;
  };
  content?: T[];
  totalElements?: number;
  totalPages?: number;
  number?: number;
  size?: number;
}

/** Normalized pagination result consumed by the UI. */
export interface PageResult<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext?: boolean;
  hasPrevious?: boolean;
}
