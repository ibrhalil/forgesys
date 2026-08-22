import { useQuery } from '@tanstack/react-query';
import { auditLogsApi, loginHistoryApi, requestLogsApi, type AuditLogParams, type LoginHistoryParams, type RequestLogParams } from './api';

export function useAuditLogs(params: AuditLogParams = {}) {
  return useQuery({ queryKey: ['audit-logs', params], queryFn: () => auditLogsApi.list(params) });
}

export function useLoginHistory(params: LoginHistoryParams = {}) {
  return useQuery({ queryKey: ['login-history', params], queryFn: () => loginHistoryApi.list(params) });
}

export function useRequestLogs(params: RequestLogParams = {}) {
  return useQuery({ queryKey: ['request-logs', params], queryFn: () => requestLogsApi.list(params) });
}
