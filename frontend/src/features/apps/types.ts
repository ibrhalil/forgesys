// ─── Enums (wire = uppercase Java enum name) ───

/** PropertyType (backend rejects FORMULA on create — deferred type). */
export type PropertyType = 'TEXT' | 'NUMBER' | 'SELECT' | 'DATE' | 'USER' | 'RELATION' | 'FORMULA';

/** ViewType — view CRUD/renderers land in a later part; typed for the detail response. */
export type ViewType = 'TABLE' | 'BOARD' | 'CALENDAR' | 'GALLERY' | 'LIST';

// ─── View filter/sort DSL (mirrors backend AppValueFilterCriteria et al.) ───

/**
 * AppValueOperator — the 9 ops of the backend value DSL. BETWEEN/IN do not exist
 * (an open range is two rows: GTE + LT/LTE). Wire = uppercase enum name.
 */
export type AppValueOperator =
  | 'EQ'
  | 'NOT_EQ'
  | 'CONTAINS'
  | 'GT'
  | 'GTE'
  | 'LT'
  | 'LTE'
  | 'IS_EMPTY'
  | 'IS_NOT_EMPTY';

/**
 * One filter row. `value` semantics per op (backend AppQueryValidator):
 * EQ/NOT_EQ → string | number; CONTAINS → string; GT/GTE/LT/LTE → number (NUMBER)
 * or 'YYYY-MM-DD' string (DATE); IS_EMPTY/IS_NOT_EMPTY → key omitted entirely.
 */
export interface AppValueFilter {
  propertyId: string;
  operator: AppValueOperator;
  value?: string | number;
}

/** One sort row — `propertyId` is a property UUID or the reserved 'createdAt'. */
export interface AppValueSort {
  propertyId: string;
  direction: 'asc' | 'desc';
}

/**
 * Typed view config (backend AppViewConfigDto). BOARD requires `groupBy` (a SELECT
 * property id); CALENDAR requires `dateProperty` (a DATE property id); TABLE/LIST/
 * GALLERY forbid both anchors.
 */
export interface AppViewConfig {
  filters?: AppValueFilter[];
  sorts?: AppValueSort[];
  /** BOARD only — SELECT property id whose options become the columns. */
  groupBy?: string;
  /** CALENDAR only — DATE property id used to place records on the grid. */
  dateProperty?: string;
}

// ─── Responses ───

export interface App {
  id: string;
  name: string;
  description: string | null;
  icon: string | null;
  createdDate: string;
  updatedAt: string;
}

/** Type-scoped property config — backend serializes only the relevant field (NON_NULL). */
export interface AppPropertyConfig {
  /** SELECT: allowed values (required, non-empty, distinct). */
  options?: string[];
  /** RELATION: target app id (required). */
  targetAppId?: string;
}

export interface AppProperty {
  id: string;
  appId: string;
  name: string;
  type: PropertyType;
  config: AppPropertyConfig | null;
  required: boolean;
  position: number;
}

export interface AppView {
  id: string;
  appId: string;
  name: string;
  type: ViewType;
  config: AppViewConfig | null;
  position: number;
}

/** AppDetailResponse — properties/views pre-sorted (position, then name). */
export interface AppDetail extends App {
  properties: AppProperty[];
  views: AppView[];
}

/**
 * Record cell values keyed by property id. JSON scalars per property type:
 * TEXT/SELECT/DATE/USER/RELATION → string, NUMBER → number.
 * Absent key = empty cell; JSON null = cleared value.
 */
export interface AppRecord {
  id: string;
  appId: string;
  values: Record<string, string | number | null>;
  createdDate: string;
  updatedAt: string;
  createdBy: string;
}

// ─── Requests ───

export interface AppRequest {
  name: string;
  description?: string;
  icon?: string;
}

export interface AppPropertyRequest {
  name: string;
  /** Immutable after create — omitted when editing an existing property. */
  type?: PropertyType;
  config?: AppPropertyConfig;
  required?: boolean;
  position?: number;
}

/** Create/patch payload — only the given keys are touched; null clears (required props reject null). */
export interface AppRecordRequest {
  values: Record<string, string | number | null>;
}

/**
 * View create/update payload (full PUT — the backend resets a missing position
 * to 0, so edits always resend the current position).
 */
export interface AppViewRequest {
  name: string;
  type: ViewType;
  config?: AppViewConfig;
  position: number;
}
