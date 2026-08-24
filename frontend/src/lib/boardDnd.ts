/**
 * Pure kanban drag-drop resolution shared by TaskBoard and RecordBoard.
 * React-free on purpose — the drop semantics (no-op / empty bucket / target
 * value) stay unit-testable without jsdom pointer simulation.
 */

/** Droppable id namespace for board columns: `col:<value>` / `col:__empty`. */
export const columnDropId = (value: string): string => `col:${value}`;

/** Droppable id of the trailing "no value" bucket (target value `null`). */
export const EMPTY_DROP_ID = columnDropId('__empty');

export interface DropColumn<TValue> {
  id: string;
  value: TValue;
}

/**
 * Resolve a DndContext `over` id into the value to write for the dragged card:
 * - `undefined` → no-op (nothing targeted, an unknown id, or the card is
 *   already in that column — same-column drops never fire a mutation);
 * - `null` → the empty bucket (clear the value);
 * - otherwise the target column's value.
 */
export function resolveDrop<TValue>(
  overId: string | number | undefined,
  currentValue: TValue,
  columns: DropColumn<TValue>[],
): TValue | undefined {
  if (overId === undefined) return undefined;
  const column = columns.find((c) => c.id === overId);
  if (!column || column.value === currentValue) return undefined;
  return column.value;
}
