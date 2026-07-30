import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { sessionsApi } from './api';

const SESSIONS_KEY = ['sessions'] as const;
const ALL_SESSIONS_KEY = ['sessions', 'all'] as const;

// ─── self ───
export function useMySessions() {
  return useQuery({ queryKey: ['sessions', 'me'], queryFn: sessionsApi.listMine });
}

export function useRevokeMySession() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (sessionId: string) => sessionsApi.revokeMine(sessionId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['sessions', 'me'] }),
  });
}

// ─── admin: per-user ───
export function useUserSessions(userId: string | undefined) {
  return useQuery({
    queryKey: SESSIONS_KEY,
    enabled: !!userId,
    queryFn: () => sessionsApi.listForUser(userId!),
  });
}

export function useRevokeUserSession() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ userId, sessionId }: { userId: string; sessionId: string }) =>
      sessionsApi.revokeForUser(userId, sessionId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: SESSIONS_KEY });
      qc.invalidateQueries({ queryKey: ALL_SESSIONS_KEY });
    },
  });
}

export function useRevokeAllUserSessions() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (userId: string) => sessionsApi.revokeAllForUser(userId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: SESSIONS_KEY });
      qc.invalidateQueries({ queryKey: ALL_SESSIONS_KEY });
    },
  });
}

// ─── admin: tenant-wide ───

/** All active sessions across the tenant (GET /sessions, iam:user:write). */
export function useAllSessions() {
  return useQuery({ queryKey: ALL_SESSIONS_KEY, queryFn: sessionsApi.listAll });
}
