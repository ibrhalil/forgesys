/**
 * Display-only date/time helpers for ISO timestamps coming back from the backend
 * (OffsetDateTime serialized as ISO-8601). Locale-independent formatting keeps the
 * audit/session tables stable across environments.
 */

const DATETIME_FMT = new Intl.DateTimeFormat('en-GB', {
  year: 'numeric',
  month: 'short',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
});

/** "30 Jul 2026, 14:05" — for table cells. Falls back to the raw input on parse failure. */
export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return '—';
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? iso : DATETIME_FMT.format(d);
}

/**
 * Coarse relative time ("2m ago", "3h ago", "yesterday") for session "last seen".
 * Intentionally low-resolution — these are approximate, not precise.
 */
export function relativeTime(iso: string | null | undefined): string {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  const seconds = Math.round((d.getTime() - Date.now()) / 1000);
  const abs = Math.abs(seconds);
  const rtf = new Intl.RelativeTimeFormat('en', { numeric: 'auto' });
  if (abs < 60) return rtf.format(seconds, 'second');
  if (abs < 3600) return rtf.format(Math.round(seconds / 60), 'minute');
  if (abs < 86400) return rtf.format(Math.round(seconds / 3600), 'hour');
  if (abs < 2592000) return rtf.format(Math.round(seconds / 86400), 'day');
  if (abs < 31536000) return rtf.format(Math.round(seconds / 2592000), 'month');
  return rtf.format(Math.round(seconds / 31536000), 'year');
}

/**
 * Best-effort browser/OS label from a raw User-Agent (e.g. "Chrome · macOS"). Falls
 * back to the raw string when nothing recognizable matches — precise UA parsing is out
 * of scope for the UI; this only needs to be human-readable.
 */
export function describeUserAgent(ua: string | null | undefined): string {
  if (!ua) return 'Unknown device';
  const browser = /Edg\//.test(ua) ? 'Edge'
    : /Chrome\//.test(ua) ? 'Chrome'
    : /Firefox\//.test(ua) ? 'Firefox'
    : /Safari\//.test(ua) ? 'Safari'
    : '';
  const os = /Windows/.test(ua) ? 'Windows'
    : /Mac OS|Macintosh/.test(ua) ? 'macOS'
    : /Android/.test(ua) ? 'Android'
    : /iPhone|iPad|iPod/.test(ua) ? 'iOS'
    : /Linux/.test(ua) ? 'Linux'
    : '';
  const label = [browser, os].filter(Boolean).join(' · ');
  return label || ua;
}
