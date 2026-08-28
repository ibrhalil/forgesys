import { describe, expect, it } from 'vitest';
import {
  decodeSearchQuery,
  encodeSearchQuery,
  readSearchQueryFromLocation,
  SEARCH_QUERY_MAX_LENGTH,
  SEARCH_QUERY_PARAM,
  type SearchQueryState,
} from '../lib/searchQuery';

/** Codec tests for the K-55 URL search-query blob: round-trips (incl. Turkish
 * text), strict charset, schema tolerance and the size cap. */

const FULL_STATE: SearchQueryState = {
  v: 1,
  page: 3,
  size: 25,
  sorts: [{ field: 'createdDate', dir: 'desc' }],
  q: 'ğüşİöç',
  qFields: ['path', 'username'],
  filters: [
    { field: 'status', operator: 'BETWEEN', values: ['400', '599'] },
    { field: 'method', operator: 'IN', values: ['GET', 'POST'] },
  ],
};

describe('encodeSearchQuery', () => {
  it('produces paddingless URL-safe base64', () => {
    const blob = encodeSearchQuery(FULL_STATE)!;
    expect(blob).toMatch(/^[A-Za-z0-9_-]+$/);
  });

  it('returns null over the size cap', () => {
    const huge: SearchQueryState = {
      v: 1,
      page: 0,
      size: 10,
      sorts: [{ field: 'path', dir: 'asc' }],
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
});

describe('decodeSearchQuery', () => {
  it('round-trips a full state including Turkish text', () => {
    const decoded = decodeSearchQuery(encodeSearchQuery(FULL_STATE)!)!;
    expect(decoded).toEqual(FULL_STATE);
  });

  it('round-trips payload sizes that need base64 padding', () => {
    for (const len of [1, 2, 3, 4, 5, 10, 31]) {
      const state: SearchQueryState = { v: 1, page: 0, size: 10, sorts: [{ field: 'a'.repeat(len), dir: 'asc' }] };
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

  it('rejects unknown versions and missing core fields', () => {
    const enc = (obj: unknown) =>
      btoa(JSON.stringify(obj)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
    expect(decodeSearchQuery(enc({ v: 2, page: 0, size: 10, sorts: [] }))).toBeNull();
    expect(decodeSearchQuery(enc({ page: 0, size: 10, sorts: [{ field: 'a', dir: 'asc' }] }))).toBeNull();
    expect(decodeSearchQuery(enc({ v: 1, size: 10, sorts: [{ field: 'a', dir: 'asc' }] }))).toBeNull();
    expect(decodeSearchQuery(enc({ v: 1, page: -1, size: 10, sorts: [{ field: 'a', dir: 'asc' }] }))).toBeNull();
    expect(decodeSearchQuery(enc({ v: 1, page: 0, size: 0, sorts: [{ field: 'a', dir: 'asc' }] }))).toBeNull();
    expect(decodeSearchQuery(enc({ v: 1, page: 0, size: 10 }))).toBeNull();
  });

  it('ignores unknown fields and drops malformed entries (schema tolerance)', () => {
    const enc = (obj: unknown) =>
      btoa(JSON.stringify(obj)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
    const blob = enc({
      v: 1,
      page: 2,
      size: 50,
      sorts: [{ field: 'status', dir: 'desc' }, { field: 42 }, { nope: true }, { field: 'path' }],
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
      page: 2,
      size: 50,
      sorts: [{ field: 'status', dir: 'desc' }, { field: 'path', dir: 'asc' }],
      qFields: ['path'],
      filters: [{ field: 'method', operator: 'EQ', values: ['GET'] }],
    });
  });

  it('drops empty optional keys from the canonical state', () => {
    const blob = encodeSearchQuery({ v: 1, page: 0, size: 10, sorts: [{ field: 'path', dir: 'asc' }] })!;
    const json = JSON.parse(new TextDecoder().decode(
      Uint8Array.from(atob(blob.replace(/-/g, '+').replace(/_/g, '/').padEnd(blob.length + ((4 - (blob.length % 4)) % 4), '=')), (c) => c.charCodeAt(0)),
    ));
    expect(json).not.toHaveProperty('q');
    expect(json).not.toHaveProperty('qFields');
    expect(json).not.toHaveProperty('filters');
  });
});

describe('readSearchQueryFromLocation', () => {
  it('reads the sq param from a search string', () => {
    const blob = encodeSearchQuery(FULL_STATE)!;
    expect(readSearchQueryFromLocation(`?${SEARCH_QUERY_PARAM}=${blob}&x=1`)).toEqual(FULL_STATE);
  });

  it('returns null without the param or with a broken value', () => {
    expect(readSearchQueryFromLocation('')).toBeNull();
    expect(readSearchQueryFromLocation('?page=3&size=25')).toBeNull();
    expect(readSearchQueryFromLocation(`?${SEARCH_QUERY_PARAM}=%2B%2F%3D`)).toBeNull();
  });
});
