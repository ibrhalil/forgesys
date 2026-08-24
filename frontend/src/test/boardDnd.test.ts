import { describe, expect, it } from 'vitest';
import { columnDropId, EMPTY_DROP_ID, resolveDrop, type DropColumn } from '../lib/boardDnd';

const COLUMNS: DropColumn<string | null>[] = [
  { id: columnDropId('Todo'), value: 'Todo' },
  { id: columnDropId('Done'), value: 'Done' },
  { id: EMPTY_DROP_ID, value: null },
];

describe('resolveDrop', () => {
  it('returns the target value when dropping on a different column', () => {
    expect(resolveDrop('col:Done', 'Todo', COLUMNS)).toBe('Done');
    expect(resolveDrop('col:Todo', 'Done', COLUMNS)).toBe('Todo');
  });

  it('maps the empty bucket to null (clear the value)', () => {
    expect(resolveDrop(EMPTY_DROP_ID, 'Todo', COLUMNS)).toBeNull();
  });

  it('no-ops (undefined) when dropped on the column the card already sits in', () => {
    expect(resolveDrop('col:Todo', 'Todo', COLUMNS)).toBeUndefined();
    // A null-valued record dropped back on the empty bucket is equally a no-op.
    expect(resolveDrop(EMPTY_DROP_ID, null, COLUMNS)).toBeUndefined();
  });

  it('no-ops when nothing valid is targeted', () => {
    expect(resolveDrop(undefined, 'Todo', COLUMNS)).toBeUndefined();
    // Unknown column ids never produce a write.
    expect(resolveDrop('col:Blocked', 'Todo', COLUMNS)).toBeUndefined();
    // Non-column ids (a card id leaking through collision detection) are ignored.
    expect(resolveDrop('r-42', 'Todo', COLUMNS)).toBeUndefined();
  });
});
