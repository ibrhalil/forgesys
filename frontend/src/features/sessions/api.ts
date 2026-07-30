import { api } from '../../lib/api';
import type { ActiveSession, AdminSession } from './types';

/**
 * Active-session management (K-28). Sessions are returned as a flat array (a user has
 * only a handful of active devices, so there is no pagination). Self endpoints are
 * authenticated-only; the admin endpoints ({@code /users/{id}/...}) require
 * {@code iam:user:write} (enforced server-side).
 */
export const sessionsApi = {
  listMine: () => api.get<ActiveSession[]>('/api/v1/users/me/sessions'),
  revokeMine: (sessionId: string) => api.delete<void>(`/api/v1/users/me/sessions/${sessionId}`),

  listForUser: (userId: string) => api.get<ActiveSession[]>(`/api/v1/users/${userId}/sessions`),
  revokeForUser: (userId: string, sessionId: string) =>
    api.delete<void>(`/api/v1/users/${userId}/sessions/${sessionId}`),
  revokeAllForUser: (userId: string) => api.delete<void>(`/api/v1/users/${userId}/sessions`),

  /** Tenant-wide "all sessions" admin view (GET /sessions, iam:user:write). */
  listAll: () => api.get<AdminSession[]>('/api/v1/sessions'),
};
