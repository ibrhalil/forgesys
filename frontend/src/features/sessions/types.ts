// Active refresh-token sessions (K-28) — one per device. Returned as a flat array
// (not paged) by /users/me/sessions and /users/{id}/sessions.
export interface ActiveSession {
  sessionId: string;
  userAgent: string | null;
  ipAddress: string | null;
  loginAt: string;
  lastSeen: string;
  /** True only on the self view, for the session behind the caller's refresh cookie. */
  current: boolean;
}

// Admin tenant-wide session (GET /sessions) — carries the owner (userId + email) so the
// "all sessions" table can show who each device belongs to.
export interface AdminSession {
  sessionId: string;
  userId: string;
  email: string;
  userAgent: string | null;
  ipAddress: string | null;
  loginAt: string;
  lastSeen: string;
}
