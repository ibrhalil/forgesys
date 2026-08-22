import { describe, expect, it } from 'vitest';
import { cellDisplay, cellEditValue, isInlineEditable, parseCellInput, shortenId } from '../features/apps/cellValue';
import type { AppProperty, AppRecord } from '../features/apps/types';

const prop = (over: Partial<AppProperty> = {}): AppProperty => ({
  id: 'p1',
  appId: 'a1',
  name: 'P',
  type: 'TEXT',
  config: null,
  required: false,
  position: 0,
  ...over,
});

const record = (values: AppRecord['values'] = {}): AppRecord => ({
  id: 'r1',
  appId: 'a1',
  values,
  createdDate: '2026-08-22T10:00:00Z',
  updatedAt: '2026-08-22T10:00:00Z',
  createdBy: 'u1',
});

describe('isInlineEditable', () => {
  it('allows TEXT/NUMBER/SELECT/DATE and rejects picker-dependent types', () => {
    expect(isInlineEditable(prop({ type: 'TEXT' }))).toBe(true);
    expect(isInlineEditable(prop({ type: 'NUMBER' }))).toBe(true);
    expect(isInlineEditable(prop({ type: 'SELECT' }))).toBe(true);
    expect(isInlineEditable(prop({ type: 'DATE' }))).toBe(true);
    expect(isInlineEditable(prop({ type: 'USER' }))).toBe(false);
    expect(isInlineEditable(prop({ type: 'RELATION' }))).toBe(false);
    expect(isInlineEditable(prop({ type: 'FORMULA' }))).toBe(false);
  });
});

describe('shortenId', () => {
  it('shortens uuids to 8 chars + ellipsis', () => {
    expect(shortenId('12345678-90ab-cdef-1234-567890abcdef')).toBe('12345678…');
  });
  it('keeps short ids as-is', () => {
    expect(shortenId('a1b2c3d4')).toBe('a1b2c3d4');
  });
});

describe('cellDisplay', () => {
  it('renders scalars as strings', () => {
    expect(cellDisplay(prop({ type: 'TEXT' }), record({ p1: 'hello' }))).toBe('hello');
    expect(cellDisplay(prop({ type: 'NUMBER' }), record({ p1: 42 }))).toBe('42');
  });
  it('renders empty for missing, null and empty-string cells', () => {
    const p = prop();
    expect(cellDisplay(p, record({}))).toBe('');
    expect(cellDisplay(p, record({ p1: null }))).toBe('');
    expect(cellDisplay(p, record({ p1: '' }))).toBe('');
  });
  it('formats DATE cells', () => {
    expect(cellDisplay(prop({ type: 'DATE' }), record({ p1: '2026-08-22' }))).toBe('22 Aug 2026');
  });
  it('shortens USER/RELATION ids', () => {
    const uuid = '12345678-90ab-cdef-1234-567890abcdef';
    expect(cellDisplay(prop({ type: 'USER' }), record({ p1: uuid }))).toBe('12345678…');
    expect(cellDisplay(prop({ type: 'RELATION' }), record({ p1: uuid }))).toBe('12345678…');
  });
});

describe('parseCellInput', () => {
  it('clears to null on blank input', () => {
    expect(parseCellInput(prop(), '  ')).toBeNull();
  });
  it('parses valid numbers and rejects NaN', () => {
    const p = prop({ type: 'NUMBER' });
    expect(parseCellInput(p, '3.14')).toBe(3.14);
    expect(parseCellInput(p, 'abc')).toBeUndefined();
  });
  it('accepts only real yyyy-mm-dd dates', () => {
    const p = prop({ type: 'DATE' });
    expect(parseCellInput(p, '2026-08-22')).toBe('2026-08-22');
    expect(parseCellInput(p, '22/08/2026')).toBeUndefined();
    expect(parseCellInput(p, '2026-13-01')).toBeUndefined();
  });
  it('accepts only configured SELECT options', () => {
    const p = prop({ type: 'SELECT', config: { options: ['todo', 'done'] } });
    expect(parseCellInput(p, 'todo')).toBe('todo');
    expect(parseCellInput(p, 'nope')).toBeUndefined();
  });
  it('passes TEXT through trimmed', () => {
    expect(parseCellInput(prop(), '  hi ')).toBe('hi');
  });
});

describe('cellEditValue', () => {
  it('seeds the editor with the raw stored value', () => {
    expect(cellEditValue(prop(), record({ p1: 'raw' }))).toBe('raw');
    expect(cellEditValue(prop({ type: 'NUMBER' }), record({ p1: 7 }))).toBe('7');
  });
  it('seeds empty for missing/null cells', () => {
    expect(cellEditValue(prop(), record({}))).toBe('');
    expect(cellEditValue(prop(), record({ p1: null }))).toBe('');
  });
});
