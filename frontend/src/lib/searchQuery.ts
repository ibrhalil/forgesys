import type { FilterCriteria } from '../types';

/**
 * Versioned, URL-safe serialization of the FILTER part of a list-page query (K-55):
 * `{v, q, qFields, filters}` — the base64url `sq` param. Paging (`page`/`size`) and
 * sorting (`sort=field,dir` params) deliberately stay OUT of the blob: they travel
 * as ordinary flat params on both the browser URL and the API wire, so the blob only
 * carries what plain params cannot express. Decoding is schema-tolerant: unknown
 * fields are ignored (legacy all-in-one blobs lose only their paging/sort), a
 * malformed or wrong-version blob yields `null` (callers fall back to defaults) —
 * shared links must survive schema evolution.
 */

/** Wire operators of the backend filter engine — the decode-side whitelist. */
const FILTER_OPERATORS = new Set([
  'EQ', 'NOT_EQ', 'IN', 'NOT_IN', 'CONTAINS', 'STARTS_WITH', 'ENDS_WITH',
  'GT', 'GTE', 'LT', 'LTE', 'BETWEEN', 'IS_NULL', 'IS_NOT_NULL',
]);

/** Hard cap on the encoded blob — mirrors the backend limit. */
export const SEARCH_QUERY_MAX_LENGTH = 4096;

/** The single query param carrying the encoded search query. */
export const SEARCH_QUERY_PARAM = 'sq';

export interface SearchQueryState {
  v: 1;
  q?: string;
  qFields?: string[];
  filters?: FilterCriteria[];
}

const BASE64URL_RE = /^[A-Za-z0-9_-]+$/;

export function encodeSearchQuery(state: SearchQueryState): string | null {
  const json = JSON.stringify(state);
  const encoded = toBase64Url(new TextEncoder().encode(json));
  return encoded.length > SEARCH_QUERY_MAX_LENGTH ? null : encoded;
}

export function decodeSearchQuery(blob: string): SearchQueryState | null {
  if (!blob || blob.length > SEARCH_QUERY_MAX_LENGTH || !BASE64URL_RE.test(blob)) return null;
  try {
    return sanitize(JSON.parse(new TextDecoder().decode(fromBase64Url(blob))));
  } catch {
    return null;
  }
}

/** Reads `sq` from a `window.location.search`-style string (no param → null). */
export function readSearchQueryFromLocation(search: string): SearchQueryState | null {
  const blob = new URLSearchParams(search).get(SEARCH_QUERY_PARAM);
  return blob ? decodeSearchQuery(blob) : null;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function sanitize(raw: unknown): SearchQueryState | null {
  if (!isRecord(raw) || raw.v !== 1) return null;

  const q = typeof raw.q === 'string' && raw.q ? raw.q : undefined;

  const qFields = Array.isArray(raw.qFields)
    ? raw.qFields.filter((f): f is string => typeof f === 'string' && !!f)
    : undefined;

  const filters: FilterCriteria[] = [];
  if (Array.isArray(raw.filters)) {
    for (const f of raw.filters) {
      if (!isRecord(f) || typeof f.field !== 'string' || !f.field) continue;
      if (typeof f.operator !== 'string' || !FILTER_OPERATORS.has(f.operator)) continue;
      if (!Array.isArray(f.values)) continue;
      const values = f.values.filter((v): v is string => typeof v === 'string');
      filters.push({ field: f.field, operator: f.operator as FilterCriteria['operator'], values });
    }
  }

  const state: SearchQueryState = { v: 1 };
  if (q) state.q = q;
  if (qFields?.length) state.qFields = qFields;
  if (filters.length) state.filters = filters;
  return state;
}

function toBase64Url(bytes: Uint8Array): string {
  let binary = '';
  const CHUNK = 0x8000; // avoid String.fromCharCode argument limits on large payloads
  for (let i = 0; i < bytes.length; i += CHUNK) {
    binary += String.fromCharCode(...bytes.subarray(i, i + CHUNK));
  }
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function fromBase64Url(blob: string): Uint8Array {
  const base64 = blob.replace(/-/g, '+').replace(/_/g, '/');
  const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4);
  const binary = atob(padded);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  return bytes;
}
