import type { AppProperty, AppRecord, PropertyType } from './types';
import { formatDate } from '../../lib/format';

/**
 * Pure helpers around app record cell values (JSON scalars keyed by property id).
 * Shared by the TABLE renderer display + inline edit and the record create modal —
 * kept free of React so they are directly unit-testable.
 */

/** Property types the UI can edit inline (USER/RELATION pickers land in a later part). */
export const INLINE_EDITABLE_TYPES: readonly PropertyType[] = ['TEXT', 'NUMBER', 'SELECT', 'DATE'];

export function isInlineEditable(prop: AppProperty): boolean {
  return INLINE_EDITABLE_TYPES.includes(prop.type);
}

/** "a1b2c3d4…" — shortened raw id for USER/RELATION cells until pickers exist. */
export function shortenId(id: string): string {
  return id.length <= 8 ? id : `${id.slice(0, 8)}…`;
}

/** Human-readable cell text for the TABLE renderer; '' for empty cells. */
export function cellDisplay(prop: AppProperty, record: AppRecord): string {
  const value = record.values[prop.id];
  if (value === undefined || value === null || value === '') return '';
  switch (prop.type) {
    case 'DATE':
      return formatDate(String(value));
    case 'USER':
    case 'RELATION':
      return shortenId(String(value));
    default:
      return String(value);
  }
}

/**
 * Parse raw editor input against the property type for a PATCH/create payload.
 * Returns the scalar to send, or `null` to clear the cell, or `undefined` when
 * the input is invalid (NaN number / bad date) and must not be submitted.
 */
export function parseCellInput(
  prop: AppProperty,
  raw: string,
): string | number | null | undefined {
  const trimmed = raw.trim();
  if (trimmed === '') return null;
  switch (prop.type) {
    case 'NUMBER': {
      const num = Number(trimmed);
      return Number.isNaN(num) ? undefined : num;
    }
    case 'DATE':
      return /^\d{4}-\d{2}-\d{2}$/.test(trimmed) && !Number.isNaN(new Date(`${trimmed}T00:00:00`).getTime())
        ? trimmed
        : undefined;
    case 'SELECT': {
      const options = prop.config?.options ?? [];
      return options.includes(trimmed) ? trimmed : undefined;
    }
    default:
      return trimmed;
  }
}

/**
 * Seed the inline editor with the raw stored value ('' when empty). Dates come back
 * as yyyy-mm-dd already; NUMBER stays a string for the input field.
 */
export function cellEditValue(prop: AppProperty, record: AppRecord): string {
  const value = record.values[prop.id];
  return value === undefined || value === null ? '' : String(value);
}

/** First TEXT property (position order) — the natural "primary field" of an app. */
export function firstTextProperty(properties: AppProperty[]): AppProperty | undefined {
  return properties.find((p) => p.type === 'TEXT');
}

/**
 * Card title for the non-TABLE renderers (BOARD/CALENDAR/LIST/GALLERY): the first
 * TEXT property's value, falling back to a shortened record id. `resolve` (when
 * provided) lets callers swap in picker-aware labels (valueLabels resolver).
 */
export function recordTitle(
  record: AppRecord,
  titleProp: AppProperty | undefined,
  resolve?: (prop: AppProperty, record: AppRecord) => string,
): string {
  if (titleProp) {
    const label = resolve?.(titleProp, record) ?? cellDisplay(titleProp, record);
    if (label !== '') return label;
  }
  return `#${shortenId(record.id)}`;
}
