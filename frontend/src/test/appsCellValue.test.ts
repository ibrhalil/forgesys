import { beforeEach, describe, expect, it } from 'vitest';
import {
  buildRecordPatch,
  cellDisplay,
  cellEditValue,
  firstTextProperty,
  isInlineEditable,
  parseCellInput,
  recordTitle,
  shortenId,
} from '../features/apps/cellValue';
import type { AppProperty, AppRecord } from '../features/apps/types';
import { useLocaleStore } from '../store/localeStore';

// DATE cells render via formatDate — pin the locale for stable assertions.
beforeEach(() => {
  useLocaleStore.setState({ locale: 'en' });
});

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

describe('firstTextProperty', () => {
  it('picks the first TEXT property in definition order', () => {
    const props = [
      prop({ id: 'a', type: 'NUMBER', position: 0 }),
      prop({ id: 'b', type: 'TEXT', position: 1 }),
      prop({ id: 'c', type: 'TEXT', position: 2 }),
    ];
    expect(firstTextProperty(props)?.id).toBe('b');
    expect(firstTextProperty([prop({ type: 'NUMBER' })])).toBeUndefined();
  });
});

describe('recordTitle', () => {
  const titleProp = prop({ id: 't', type: 'TEXT' });
  it('uses the title property value when present', () => {
    expect(recordTitle(record({ t: 'Card title' }), titleProp)).toBe('Card title');
  });
  it('prefers the resolver label and falls back to the shortened id', () => {
    expect(recordTitle(record({ t: 'raw' }), titleProp, () => 'Resolved')).toBe('Resolved');
    expect(recordTitle(record({ t: null }), titleProp)).toBe('#r1');
    expect(recordTitle(record({}), undefined)).toBe('#r1');
    expect(recordTitle({ ...record({}), id: '12345678-90ab-cdef-1234-567890abcdef' }, undefined)).toBe('#12345678…');
  });
});

describe('buildRecordPatch (PATCH partial-merge diff)', () => {
  const props = [
    prop({ id: 'p-text', type: 'TEXT' }),
    prop({ id: 'p-num', type: 'NUMBER' }),
    prop({ id: 'p-user', type: 'USER' }),
  ];
  const stored: AppRecord = record({ 'p-text': 'Old', 'p-num': 5, 'p-user': 'u-1' });

  it('sends only changed keys; untouched and unchanged keys stay absent', () => {
    const { invalid, values } = buildRecordPatch(props, stored, {
      'p-text': 'New', // changed
      'p-num': '5', // unchanged (parses to the stored number)
      'p-user': 'u-1', // unchanged id
    });
    expect(invalid).toEqual([]);
    expect(values).toEqual({ 'p-text': 'New' });
  });

  it('clears emptied cells with null; already-empty cells are no-ops', () => {
    const { values } = buildRecordPatch(props, stored, {
      'p-text': 'Old',
      'p-num': '', // emptied → null clear
      'p-user': 'u-1',
    });
    expect(values).toEqual({ 'p-num': null });
    // Same draft against a record whose cell is already empty → nothing to send.
    const empty = buildRecordPatch(props, record({}), { 'p-num': '' });
    expect(empty.values).toEqual({});
  });

  it('passes USER/RELATION ids through unparsed and flags invalid scalars', () => {
    const { invalid, values } = buildRecordPatch(props, stored, {
      'p-text': 'Old', // unchanged
      'p-user': 'u-2', // picker id — accepted as-is
      'p-num': 'abc', // NaN number
    });
    expect(invalid).toEqual(['p-num']);
    expect(values).toEqual({ 'p-user': 'u-2' });
  });

  it('create mode (empty record) treats every filled field as a change', () => {
    const { values } = buildRecordPatch(props, record({}), {
      'p-text': 'First',
      'p-num': '3.14',
    });
    expect(values).toEqual({ 'p-text': 'First', 'p-num': 3.14 });
  });
});
