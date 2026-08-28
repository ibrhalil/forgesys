import { describe, expect, it } from 'vitest';
import {
  decodeSearchQuery,
  encodeSearchQuery,
  readSearchQueryFromLocation,
  SEARCH_QUERY_MAX_LENGTH,
  SEARCH_QUERY_PARAM,
  type SearchQueryState,
} from '../lib/searchQuery';

/** Codec tests for the K-55 filter blob (`sq`): round-trips (incl. Turkish text),
 * strict charset, schema tolerance, the size cap, and the wire-shape contract
 * lock (the key names the backend's SearchRequest deserializes). */

const FULL_STATE: SearchQueryState = {
  v: 1,
  q: 'ğüşİöç',
  qFields: ['path', 'username'],
  filters: [
    { field: 'status', operator: 'BETWEEN', values: ['400', '599'] },
    { field: 'method', operator: 'IN', values: ['GET', 'POST'] },
  ],
};

const decodeBlobToJson = (blob: string): Record<string, unknown> => {
  const padded = blob.replace(/-/g, '+').replace(/_/g, '/').padEnd(blob.length + ((4 - (blob.length % 4)) % 4), '=');
  return JSON.parse(atob(padded));
};

const enc = (obj: unknown) =>
  btoa(JSON.stringify(obj)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');

describe('encodeSearchQuery', () => {
  it('produces paddingless URL-safe base64', () => {
    const blob = encodeSearchQuery(FULL_STATE)!;
    expect(blob).toMatch(/^[A-Za-z0-9_-]+$/);
  });

  it('returns null over the size cap', () => {
    const huge: SearchQueryState = {
      v: 1,
      filters: Array.from({ length: 80 }, (_, i) => ({
        field: `field${i}`,
        operator: 'CONTAINS' as const,
        values: ['x'.repeat(60)],
      })),
    };
    const blob = encodeSearchQuery(huge);
    expect(blob === null || blob.length <= SEARCH_QUERY_MAX_LENGTH).toBe(true);
    const over = encodeSearchQuery({
      ...huge,
      filters: Array.from({ length: 500 }, (_, i) => ({
        field: `field${i}`,
        operator: 'CONTAINS' as const,
        values: ['x'.repeat(200)],
      })),
    });
    expect(over).toBeNull();
  });

  it('locks the wire shape: exactly {v, q?, qFields?, filters?} with {field, operator, values} clauses', () => {
    // Schema assertion, NOT a round-trip: both sides of the codec must keep these
    // exact key names or the backend's SearchRequest silently drops them (the
    // `dir`/`direction` bug class this test exists to prevent).
    const json = decodeBlobToJson(encodeSearchQuery(FULL_STATE)!) as Record<string, unknown>;
    expect(Object.keys(json).sort()).toEqual(['filters', 'q', 'qFields', 'v']);
    const clauses = json.filters as Record<string, unknown>[];
    clauses.forEach((c) => expect(Object.keys(c).sort()).toEqual(['field', 'operator', 'values']));
  });

  it('drops empty optional keys from the canonical state', () => {
    const json = decodeBlobToJson(encodeSearchQuery({ v: 1 })!);
    expect(json).not.toHaveProperty('q');
    expect(json).not.toHaveProperty('qFields');
    expect(json).not.toHaveProperty('filters');
  });
});

describe('decodeSearchQuery', () => {
  it('round-trips a full state including Turkish text', () => {
    const decoded = decodeSearchQuery(encodeSearchQuery(FULL_STATE)!)!;
    expect(decoded).toEqual(FULL_STATE);
  });

  it('round-trips payload sizes that need base64 padding', () => {
    for (const len of [1, 2, 3, 4, 5, 10, 31]) {
      const state: SearchQueryState = { v: 1, q: 'a'.repeat(len) };
      const blob = encodeSearchQuery(state)!;
      expect(blob).not.toContain('=');
      expect(decodeSearchQuery(blob)).toEqual(state);
    }
  });

  it('rejects garbage, empty and non-base64url input', () => {
    expect(decodeSearchQuery('')).toBeNull();
    expect(decodeSearchQuery('!!!')).toBeNull();
    expect(decodeSearchQuery('a+b/c=')).toBeNull();
    expect(decodeSearchQuery('not base64url ö')).toBeNull();
  });

  it('rejects valid base64 that is not valid JSON', () => {
    const garbage = btoa('this is not json').replace(/=+$/, '');
    expect(garbage).toMatch(/^[A-Za-z0-9_-]+$/);
    expect(decodeSearchQuery(garbage)).toBeNull();
  });

  it('rejects unknown versions', () => {
    expect(decodeSearchQuery(enc({ v: 2, q: 'x' }))).toBeNull();
    expect(decodeSearchQuery(enc({ q: 'x' }))).toBeNull();
  });

  it('tolerates legacy all-in-one blobs: paging/sorts ignored, filters kept', () => {
    const blob = enc({
      v: 1,
      page: 3,
      size: 25,
      sorts: [{ field: 'createdDate', direction: 'desc' }],
      q: 'legacy',
      filters: [{ field: 'method', operator: 'EQ', values: ['GET'] }],
    });
    expect(decodeSearchQuery(blob)).toEqual({
      v: 1,
      q: 'legacy',
      filters: [{ field: 'method', operator: 'EQ', values: ['GET'] }],
    });
  });

  it('ignores unknown fields and drops malformed entries (schema tolerance)', () => {
    const blob = enc({
      v: 1,
      futureField: { anything: true },
      qFields: ['path', 7, null],
      filters: [
        { field: 'method', operator: 'EQ', values: ['GET'] },
        { field: 'bad', operator: 'NOT_AN_OPERATOR', values: ['x'] },
        { field: 'alsoBad', operator: 'EQ' },
        'nonsense',
      ],
    });
    expect(decodeSearchQuery(blob)).toEqual({
      v: 1,
      qFields: ['path'],
      filters: [{ field: 'method', operator: 'EQ', values: ['GET'] }],
    });
  });
});

describe('readSearchQueryFromLocation', () => {
  it('reads the sq param from a search string', () => {
    const blob = encodeSearchQuery(FULL_STATE)!;
    expect(readSearchQueryFromLocation(`?page=3&size=25&${SEARCH_QUERY_PARAM}=${blob}&x=1`)).toEqual(FULL_STATE);
  });

  it('returns null without the param or with a broken value', () => {
    expect(readSearchQueryFromLocation('')).toBeNull();
    expect(readSearchQueryFromLocation('?page=3&size=25')).toBeNull();
    expect(readSearchQueryFromLocation(`?${SEARCH_QUERY_PARAM}=%2B%2F%3D`)).toBeNull();
  });
});
