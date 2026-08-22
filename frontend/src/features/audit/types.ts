// Audit & login history (K-19) — exposed by GET /audit-logs and /login-history
export interface AuditLog {
  id: string;
  actorId: string | null;
  actorName: string;
  action: string;
  entityType: string;
  entityId: string | null;
  entityName: string | null;
  ipAddress: string | null;
  traceId: string | null;
  createdAt: string;
}

export interface LoginHistory {
  id: string;
  userId: string | null;
  username: string;
  success: boolean;
  reason: string | null;
  ipAddress: string | null;
  userAgent: string | null;
  createdAt: string;
}

// Request/trace log (K-19 layer 3 + K-27) — exposed by GET /request-logs
export interface RequestLog {
  id: string;
  traceId: string | null;
  method: string | null;
  path: string | null;
  status: number | null;
  durationMs: number | null;
  userId: string | null;
  username: string | null;
  ipAddress: string | null;
  userAgent: string | null;
  requestBody: string | null;
  createdAt: string;
}
