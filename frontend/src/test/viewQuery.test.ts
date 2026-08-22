import { describe, expect, it } from 'vitest';
import {
  allowedOperators,
  applyViewQuery,
  matchFilter,
  operatorNeedsValue,
  toWireFilter,
} from '../features/apps/viewQuery';
import type { AppProperty, AppRecord, PropertyType } from '../features/apps/types';

const prop = (id: string, type: PropertyType, over: Partial<AppProperty> = {}): AppProperty => ({
  id,
  appId: 'a1',
  name: id,
  type,
  config: null,
  required: false,
  position: 0,
  ...over,
});

const record = (
  id: string,
  values: Record<string, string | number | null>,
  createdDate = '2026-08-01T00:00:00Z',
): AppRecord => ({
  id,
  appId: 'a1',
  values,
  createdDate,
  updatedAt: createdDate,
  createdBy: 'u1',
});

describe('allowedOperators', () => {
  it('matches the backend matrix per property type', () => {
    expect(allowedOperators('TEXT')).toEqual(['EQ', 'NOT_EQ', 'CONTAINS', 'IS_EMPTY', 'IS_NOT_EMPTY']);
    expect(allowedOperators('NUMBER')).toEqual([
      'EQ', 'NOT_EQ', 'GT', 'GTE', 'LT', 'LTE', 'IS_EMPTY', 'IS_NOT_EMPTY',
    ]);
    expect(allowedOperators('SELECT')).toEqual(['EQ', 'NOT_EQ', 'IS_EMPTY', 'IS_NOT_EMPTY']);
    expect(allowedOperators('USER')).toEqual(['EQ', 'NOT_EQ', 'IS_EMPTY', 'IS_NOT_EMPTY']);
    expect(allowedOperators('RELATION')).toEqual(['EQ', 'NOT_EQ', 'IS_EMPTY', 'IS_NOT_EMPTY']);
    expect(allowedOperators('FORMULA')).toEqual([]);
  });

  it('marks only the empty-checks as value-less', () => {
    expect(operatorNeedsValue('IS_EMPTY')).toBe(false);
    expect(operatorNeedsValue('IS_NOT_EMPTY')).toBe(false);
    expect(operatorNeedsValue('EQ')).toBe(true);
    expect(operatorNeedsValue('CONTAINS')).toBe(true);
  });
});

describe('matchFilter (executor parity)', () => {
  const text = prop('p-text', 'TEXT');

  it('IS_EMPTY matches absent, null and empty-string cells', () => {
    const f = { propertyId: 'p-text', operator: 'IS_EMPTY' } as const;
    expect(matchFilter(text, record('r1', {}), f)).toBe(true);
    expect(matchFilter(text, record('r1', { 'p-text': null }), f)).toBe(true);
    expect(matchFilter(text, record('r1', { 'p-text': '' }), f)).toBe(true);
    expect(matchFilter(text, record('r1', { 'p-text': 'x' }), f)).toBe(false);
  });

  it('IS_NOT_EMPTY is the inverse', () => {
    const f = { propertyId: 'p-text', operator: 'IS_NOT_EMPTY' } as const;
    expect(matchFilter(text, record('r1', {}), f)).toBe(false);
    expect(matchFilter(text, record('r1', { 'p-text': 'x' }), f)).toBe(true);
  });

  it('value operators never match an empty cell — except NOT_EQ (NOT EXISTS semantics)', () => {
    const empty = record('r1', {});
    expect(matchFilter(text, empty, { propertyId: 'p-text', operator: 'EQ', value: 'x' })).toBe(false);
    expect(matchFilter(text, empty, { propertyId: 'p-text', operator: 'CONTAINS', value: 'x' })).toBe(false);
    expect(matchFilter(text, empty, { propertyId: 'p-text', operator: 'NOT_EQ', value: 'x' })).toBe(true);
  });

  it('EQ compares text exactly and NUMBER numerically', () => {
    expect(matchFilter(text, record('r1', { 'p-text': 'Open' }), { propertyId: 'p-text', operator: 'EQ', value: 'Open' })).toBe(true);
    expect(matchFilter(text, record('r1', { 'p-text': 'Open' }), { propertyId: 'p-text', operator: 'EQ', value: 'open' })).toBe(false);
    const num = prop('p-num', 'NUMBER');
    expect(matchFilter(num, record('r1', { 'p-num': 5 }), { propertyId: 'p-num', operator: 'EQ', value: 5 })).toBe(true);
    expect(matchFilter(num, record('r1', { 'p-num': 5 }), { propertyId: 'p-num', operator: 'NOT_EQ', value: 7 })).toBe(true);
  });

  it('CONTAINS is case-insensitive substring', () => {
    expect(matchFilter(text, record('r1', { 'p-text': 'Order Tracking' }), { propertyId: 'p-text', operator: 'CONTAINS', value: 'track' })).toBe(true);
    expect(matchFilter(text, record('r1', { 'p-text': 'Order' }), { propertyId: 'p-text', operator: 'CONTAINS', value: 'zzz' })).toBe(false);
  });

  it('GT/GTE/LT/LTE compare NUMBER numerically and DATE as ISO text', () => {
    const num = prop('p-num', 'NUMBER');
    const r = record('r1', { 'p-num': 10 });
    expect(matchFilter(num, r, { propertyId: 'p-num', operator: 'GT', value: 5 })).toBe(true);
    expect(matchFilter(num, r, { propertyId: 'p-num', operator: 'GT', value: 10 })).toBe(false);
    expect(matchFilter(num, r, { propertyId: 'p-num', operator: 'GTE', value: 10 })).toBe(true);
    expect(matchFilter(num, r, { propertyId: 'p-num', operator: 'LT', value: 5 })).toBe(false);
    expect(matchFilter(num, r, { propertyId: 'p-num', operator: 'LTE', value: 10 })).toBe(true);

    const date = prop('p-date', 'DATE');
    const rd = record('r1', { 'p-date': '2026-08-02' });
    expect(matchFilter(date, rd, { propertyId: 'p-date', operator: 'GTE', value: '2026-08-01' })).toBe(true);
    expect(matchFilter(date, rd, { propertyId: 'p-date', operator: 'GTE', value: '2026-08-02' })).toBe(true);
    expect(matchFilter(date, rd, { propertyId: 'p-date', operator: 'LT', value: '2026-08-02' })).toBe(false);
  });
});

describe('applyViewQuery', () => {
  const properties = [
    prop('p-title', 'TEXT'),
    prop('p-num', 'NUMBER'),
    prop('p-status', 'SELECT', { config: { options: ['open', 'done'] } }),
  ];
  const records = [
    record('r-old', { 'p-title': 'Old', 'p-num': 2, 'p-status': 'open' }, '2026-08-01T00:00:00Z'),
    record('r-new', { 'p-title': 'New', 'p-num': 10, 'p-status': 'done' }, '2026-08-04T00:00:00Z'),
    record('r-mid', { 'p-title': 'Middle', 'p-num': 5, 'p-status': 'done' }, '2026-08-03T00:00:00Z'),
    record('r-empty', {}, '2026-08-02T00:00:00Z'),
  ];

  it('AND-combines filters', () => {
    const out = applyViewQuery(records, properties, [
      { propertyId: 'p-status', operator: 'EQ', value: 'done' },
      { propertyId: 'p-num', operator: 'GTE', value: 6 },
    ]);
    expect(out.map((r) => r.id)).toEqual(['r-new']);
  });

  it('sorts by property asc/desc with PG null-ordering (asc → nulls last, desc → nulls first)', () => {
    const asc = applyViewQuery(records, properties, [], [{ propertyId: 'p-num', direction: 'asc' }]);
    expect(asc.map((r) => r.id)).toEqual(['r-old', 'r-mid', 'r-new', 'r-empty']);
    const desc = applyViewQuery(records, properties, [], [{ propertyId: 'p-num', direction: 'desc' }]);
    expect(desc.map((r) => r.id)).toEqual(['r-empty', 'r-new', 'r-mid', 'r-old']);
  });

  it('applies sorts in order (multi-key) and supports createdAt', () => {
    const out = applyViewQuery(records, properties, [], [
      { propertyId: 'p-status', direction: 'asc' },
      { propertyId: 'p-num', direction: 'desc' },
    ]);
    // done group first (asc: done < open, nulls last), numbers desc inside.
    expect(out.map((r) => r.id)).toEqual(['r-new', 'r-mid', 'r-old', 'r-empty']);
    const byCreated = applyViewQuery(records, properties, [], [{ propertyId: 'createdAt', direction: 'desc' }]);
    expect(byCreated.map((r) => r.id)).toEqual(['r-new', 'r-mid', 'r-empty', 'r-old']);
  });

  it('skips filters/sorts referencing unknown properties (deleted after save)', () => {
    const out = applyViewQuery(records, properties, [{ propertyId: 'p-gone', operator: 'EQ', value: 'x' }], []);
    expect(out).toHaveLength(records.length);
  });
});

describe('toWireFilter', () => {
  it('keeps the value for value operators and omits it for empty-checks', () => {
    expect(toWireFilter({ propertyId: 'p', operator: 'EQ', value: 'x' })).toEqual({
      propertyId: 'p',
      operator: 'EQ',
      value: 'x',
    });
    expect(toWireFilter({ propertyId: 'p', operator: 'IS_EMPTY', value: 'stale' })).toEqual({
      propertyId: 'p',
      operator: 'IS_EMPTY',
    });
  });
});
