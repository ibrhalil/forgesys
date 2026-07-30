import { useQuery } from '@tanstack/react-query';
import { auditLogsApi, loginHistoryApi, type AuditLogParams, type LoginHistoryParams } from '../api/audit';

export function useAuditLogs(params: AuditLogParams = {}) {
  return useQuery({ queryKey: ['audit-logs', params], queryFn: () => auditLogsApi.list(params) });
}

export function useLoginHistory(params: LoginHistoryParams = {}) {
  return useQuery({ queryKey: ['login-history', params], queryFn: () => loginHistoryApi.list(params) });
}
