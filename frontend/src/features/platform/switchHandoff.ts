import { authApi } from '../auth/authApi';
import { useAuthStore } from '../../store/authStore';

/**
 * Consumes a one-time {@code ?switchCode=} on the current tenant URL (K-50 F6):
 * strips the param from the address FIRST — the code is one-time and React
 * StrictMode runs effects twice — then exchanges it for the impersonation
 * session and refreshes {@code /me} so the impersonation banner data lands.
 * Returns false when no code was present; a failed exchange throws (caller
 * toasts).
 */
export async function consumeSwitchCode(): Promise<boolean> {
  const params = new URLSearchParams(window.location.search);
  const code = params.get('switchCode');
  if (!code) return false;
  params.delete('switchCode');
  const qs = params.toString();
  window.history.replaceState(
    null,
    '',
    `${window.location.pathname}${qs ? `?${qs}` : ''}${window.location.hash}`,
  );
  await authApi.exchangePlatformSwitch(code);
  await useAuthStore.getState().fetchMe();
  return true;
}
