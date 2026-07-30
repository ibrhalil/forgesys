import { toast } from 'react-toastify';
import { ApiError } from './api';
import { t } from './i18n';

/** Thin imperative notification surface (kept in one place so callers don't import
 *  react-toastify directly and the wording/behaviour is uniform). */
export const notify = {
  success: (msg: string) => toast.success(msg),
  error: (msg: string) => toast.error(msg),
  warning: (msg: string) => toast.warning(msg),
  info: (msg: string) => toast.info(msg),
};

/** Map any thrown value to a human-readable message. */
export function errorMessage(err: unknown): string {
  if (err instanceof ApiError) return err.body.message || t('common.unexpectedError');
  if (err instanceof Error) return err.message || t('common.unexpectedError');
  return t('common.unexpectedError');
}

/**
 * Global mutation/query error handler. Toasts non-validation errors. It deliberately
 * <strong>skips</strong> two cases so notifications never double up with other layers:
 * <ul>
 *   <li>{@code ApiError} carrying {@code fields[]} — field-level validation belongs
 *       inline next to the offending input (see {@link extractFieldErrors}); the form
 *       catches it and renders it, so the global layer stays silent.</li>
 *   <li>{@code 401} — an expired/invalid session is handled by the {@code authStore}
 *       redirect to {@code /login}; a toast would flash before that redirect.</li>
 * </ul>
 * Every other failure (network, 403, 404, 409 business rule, 500…) surfaces as a toast,
 * which also covers the silent {@code catch { /* noop * / }} delete flows.
 */
export function notifyApiError(err: unknown): void {
  if (err instanceof ApiError) {
    if (err.status === 401) return;
    if (err.body.fields?.length) return;
    toast.error(err.body.message || t('common.unexpectedError'));
    return;
  }
  toast.error(errorMessage(err));
}

/**
 * Extract backend field-level validation ({@code ApiErrorResponse.fields[]}) into a
 * {@code field -> message} map for inline rendering next to the offending input. Returns
 * an empty object when there are no field errors (non-validation failure) — callers
 * then fall back to the global toast. This is the single shared helper (previously
 * inlined only in {@code RegisterPage}).
 */
export function extractFieldErrors(err: unknown): Record<string, string> {
  if (!(err instanceof ApiError)) return {};
  const mapped: Record<string, string> = {};
  for (const f of err.body.fields ?? []) {
    if (f.field) mapped[f.field] = f.message;
  }
  return mapped;
}
