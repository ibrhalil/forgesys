import type { ListQuerySnapshot } from '../types';

/**
 * Named list views persisted per table in localStorage (K-55 F7, v1 — browser-local
 * by decision; a DB-backed cross-device version is a later, migration-carrying step).
 * The stored payload is the full list-query snapshot (`ListQuerySnapshot`: paging +
 * sorting + the filter blob), so applying a view is identical to opening a shared
 * link. Legacy snapshots saved by the pre-split shape (paging inside `sq`) degrade
 * gracefully — the codec ignores unknown fields.
 */

export interface SavedView {
  id: string;
  name: string;
  state: ListQuerySnapshot;
  createdAt: string;
}

const PREFIX = 'sf_table_views_';

function storageKeyFor(key: string): string {
  return `${PREFIX}${key}`;
}

function isSavedView(value: unknown): value is SavedView {
  if (typeof value !== 'object' || value === null) return false;
  const v = value as Record<string, unknown>;
  return typeof v.id === 'string' && typeof v.name === 'string'
    && typeof v.state === 'object' && v.state !== null
    && (v.state as Record<string, unknown>).v === 1;
}

export function listSavedViews(key: string): SavedView[] {
  try {
    const raw = localStorage.getItem(storageKeyFor(key));
    if (!raw) return [];
    const parsed: unknown = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed.filter(isSavedView) : [];
  } catch {
    return [];
  }
}

/** Saves (or replaces, matched case-insensitively by name) the current query state. */
export function saveSavedView(key: string, name: string, state: ListQuerySnapshot): SavedView | null {
  const trimmed = name.trim();
  if (!trimmed) return null;
  const views = listSavedViews(key);
  const existing = views.find((v) => v.name.toLowerCase() === trimmed.toLowerCase());
  const view: SavedView = {
    id: existing?.id ?? (globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(36).slice(2)}`),
    name: trimmed,
    state,
    createdAt: new Date().toISOString(),
  };
  const next = existing ? views.map((v) => (v.id === view.id ? view : v)) : [...views, view];
  try {
    localStorage.setItem(storageKeyFor(key), JSON.stringify(next));
  } catch {
    return null; // quota exceeded / private mode — the in-memory list is untouched
  }
  return view;
}

export function deleteSavedView(key: string, id: string): void {
  const next = listSavedViews(key).filter((v) => v.id !== id);
  try {
    localStorage.setItem(storageKeyFor(key), JSON.stringify(next));
  } catch {
    // ignore storage failures — deleting is best-effort
  }
}
