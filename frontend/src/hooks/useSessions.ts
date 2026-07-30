import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { sessionsApi } from '../api/sessions';

const SESSIONS_KEY = ['sessions'] as const;

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

// ─── admin ───
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
    onSuccess: () => qc.invalidateQueries({ queryKey: SESSIONS_KEY }),
  });
}

export function useRevokeAllUserSessions() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (userId: string) => sessionsApi.revokeAllForUser(userId),
    onSuccess: () => qc.invalidateQueries({ queryKey: SESSIONS_KEY }),
  });
}
