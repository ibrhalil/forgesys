import type {
  CustomAppProperty,
  CustomAppRecord,
  CustomAppValueFilter,
  CustomAppValueOperator,
  CustomAppValueSort,
  PropertyType,
} from './types';

/**
 * Client-side mirror of the backend value DSL (AppQueryValidator +
 * AppRecordSearchExecutor). View renderers fetch one bounded page over plain
 * GET /records (the structured /records/search endpoint is PostgreSQL-only) and
 * apply the view's filters/sorts locally through here — semantics deliberately
 * match the executor so a future switch to server-side search is transparent.
 */

/** Operators allowed per property type — the AppQueryValidator matrix. */
const OPERATORS_BY_TYPE: Record<PropertyType, readonly CustomAppValueOperator[]> = {
  TEXT: ['EQ', 'NOT_EQ', 'CONTAINS', 'IS_EMPTY', 'IS_NOT_EMPTY'],
  NUMBER: ['EQ', 'NOT_EQ', 'GT', 'GTE', 'LT', 'LTE', 'IS_EMPTY', 'IS_NOT_EMPTY'],
  DATE: ['EQ', 'NOT_EQ', 'GT', 'GTE', 'LT', 'LTE', 'IS_EMPTY', 'IS_NOT_EMPTY'],
  SELECT: ['EQ', 'NOT_EQ', 'IS_EMPTY', 'IS_NOT_EMPTY'],
  USER: ['EQ', 'NOT_EQ', 'IS_EMPTY', 'IS_NOT_EMPTY'],
  RELATION: ['EQ', 'NOT_EQ', 'IS_EMPTY', 'IS_NOT_EMPTY'],
  // FORMULA is rejected on create — never queryable.
  FORMULA: [],
};

export function allowedOperators(type: PropertyType): readonly CustomAppValueOperator[] {
  return OPERATORS_BY_TYPE[type];
}

/** IS_EMPTY/IS_NOT_EMPTY take no value (the backend rejects a non-null one). */
export function operatorNeedsValue(op: CustomAppValueOperator): boolean {
  return op !== 'IS_EMPTY' && op !== 'IS_NOT_EMPTY';
}

function isEmptyCell(value: string | number | null | undefined): boolean {
  return value === undefined || value === null || value === '';
}

/** @>-containment equality: numeric compare for NUMBER, exact string otherwise. */
function valueEquals(prop: CustomAppProperty, cell: string | number, filter: string | number): boolean {
  if (prop.type === 'NUMBER') return Number(cell) === Number(filter);
  return String(cell) === String(filter);
}

/** GT/GTE/LT/LTE comparand: numeric for NUMBER, ISO text (lexicographic) for DATE. */
function compareValues(prop: CustomAppProperty, cell: string | number, filter: string | number): number {
  if (prop.type === 'NUMBER') {
    const a = Number(cell);
    const b = Number(filter);
    return a < b ? -1 : a > b ? 1 : 0;
  }
  const a = String(cell);
  const b = String(filter);
  return a < b ? -1 : a > b ? 1 : 0;
}

export function matchFilter(prop: CustomAppProperty, record: CustomAppRecord, filter: CustomAppValueFilter): boolean {
  const cell = record.values[filter.propertyId];
  const empty = isEmptyCell(cell);
  // Non-empty narrows cell to string | number (isEmptyCell rejects undefined/null/'').
  const value = cell as string | number;
  switch (filter.operator) {
    case 'IS_EMPTY':
      return empty;
    case 'IS_NOT_EMPTY':
      return !empty;
    case 'NOT_EQ':
      // Executor: NOT EXISTS(value = x) — a record without a value row matches too.
      return empty || !valueEquals(prop, value, filter.value!);
    case 'EQ':
      return !empty && valueEquals(prop, value, filter.value!);
    case 'CONTAINS':
      // ILIKE '%x%' — case-insensitive substring over the raw text.
      return !empty && String(value).toLowerCase().includes(String(filter.value ?? '').toLowerCase());
    case 'GT':
    case 'GTE':
    case 'LT':
    case 'LTE': {
      if (empty) return false;
      const cmp = compareValues(prop, value, filter.value!);
      if (filter.operator === 'GT') return cmp > 0;
      if (filter.operator === 'GTE') return cmp >= 0;
      if (filter.operator === 'LT') return cmp < 0;
      return cmp <= 0;
    }
    default:
      return true;
  }
}

/** Sort comparator for one criterion; empty cells follow the PG null-ordering defaults (asc→last, desc→first). */
function compareForSort(
  prop: CustomAppProperty | undefined,
  recordA: CustomAppRecord,
  recordB: CustomAppRecord,
  sort: CustomAppValueSort,
): number {
  const desc = sort.direction === 'desc';
  let a: string | number | null;
  let b: string | number | null;
  if (sort.propertyId === 'createdAt') {
    a = recordA.createdDate;
    b = recordB.createdDate;
  } else {
    a = recordA.values[sort.propertyId] ?? null;
    b = recordB.values[sort.propertyId] ?? null;
    if (isEmptyCell(a)) a = null;
    if (isEmptyCell(b)) b = null;
    if (prop?.type === 'NUMBER' && a !== null && b !== null) {
      a = Number(a);
      b = Number(b);
    } else if (a !== null && b !== null) {
      a = String(a);
      b = String(b);
    }
  }
  if (a === null && b === null) return 0;
  if (a === null) return desc ? -1 : 1;
  if (b === null) return desc ? 1 : -1;
  const cmp = a < b ? -1 : a > b ? 1 : 0;
  return desc ? -cmp : cmp;
}

/**
 * Apply a view's filter/sort clauses client-side (filters AND-combined, sorts in
 * order, `createdAt DESC` + id as deterministic tiebreakers — executor parity).
 * Filters referencing unknown properties are skipped (defensive: a property may
 * have been deleted after the view was saved).
 */
export function applyViewQuery(
  records: CustomAppRecord[],
  properties: CustomAppProperty[],
  filters: CustomAppValueFilter[] = [],
  sorts: CustomAppValueSort[] = [],
): CustomAppRecord[] {
  const byId = new Map(properties.map((p) => [p.id, p]));
  const filtered = records.filter((r) =>
    filters.every((f) => {
      const prop = byId.get(f.propertyId);
      return !prop || matchFilter(prop, r, f);
    }),
  );
  return [...filtered].sort((a, b) => {
    for (const sort of sorts) {
      const cmp = compareForSort(byId.get(sort.propertyId), a, b, sort);
      if (cmp !== 0) return cmp;
    }
    if (a.createdDate !== b.createdDate) return b.createdDate.localeCompare(a.createdDate);
    return a.id.localeCompare(b.id);
  });
}

/** Build the wire filter (omits `value` entirely for the no-value operators). */
export function toWireFilter(filter: CustomAppValueFilter): CustomAppValueFilter {
  return operatorNeedsValue(filter.operator)
    ? { propertyId: filter.propertyId, operator: filter.operator, value: filter.value }
    : { propertyId: filter.propertyId, operator: filter.operator };
}
