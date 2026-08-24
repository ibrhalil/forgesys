export type TableViewMode = 'table' | 'card' | 'list';

export interface TablePreferences {
  /** Column keys that the user has chosen to hide. */
  hiddenColumns?: string[];
  /** Preferred page size (rows per page) for this table. */
  pageSize?: number;
  /** Optional column ordering for future drag-and-drop / reordering support. */
  columnOrder?: string[];
  /** Optional table display density for future customization. */
  density?: 'compact' | 'normal' | 'relaxed';
  /** Target search column/field keys for smart search. Empty or omitted indicates all searchable fields. */
  searchFields?: string[];
  /** Active view mode (table, card grid, or compact list). */
  viewMode?: TableViewMode;
  /** Extensible key-value dictionary for future table customization needs. */
  [key: string]: unknown;
}

const STORAGE_PREFIX = 'sf_table_prefs_';

export function getTableStorageKey(key: string): string {
  return `${STORAGE_PREFIX}${key}`;
}

/**
 * Loads table preferences for the given storage key from localStorage.
 * Returns an empty object if no preferences exist, if key is omitted, or on any error.
 */
export function loadTablePreferences(key?: string): TablePreferences {
  if (!key || typeof window === 'undefined') return {};
  try {
    const raw = window.localStorage.getItem(getTableStorageKey(key));
    if (!raw) return {};
    const parsed = JSON.parse(raw);
    return typeof parsed === 'object' && parsed !== null ? parsed : {};
  } catch {
    return {};
  }
}

/**
 * Merges and saves partial table preferences into localStorage under the given key.
 * Safely handles storage quotas, disabled localStorage, or missing keys.
 */
export function saveTablePreferences(
  key: string | undefined,
  patch: Partial<TablePreferences>,
): TablePreferences {
  if (!key || typeof window === 'undefined') return patch;
  try {
    const existing = loadTablePreferences(key);
    const updated: TablePreferences = {
      ...existing,
      ...patch,
    };
    window.localStorage.setItem(getTableStorageKey(key), JSON.stringify(updated));
    return updated;
  } catch {
    return patch;
  }
}

/**
 * Clears stored preferences for the given table key, restoring default configuration.
 */
export function resetTablePreferences(key?: string): void {
  if (!key || typeof window === 'undefined') return;
  try {
    window.localStorage.removeItem(getTableStorageKey(key));
  } catch {
    // silently ignore storage errors
  }
}
