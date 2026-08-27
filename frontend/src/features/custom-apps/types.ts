// ─── Enums (wire = uppercase Java enum name) ───

/** PropertyType (backend rejects FORMULA on create — deferred type). */
export type PropertyType = 'TEXT' | 'NUMBER' | 'SELECT' | 'DATE' | 'USER' | 'RELATION' | 'FORMULA';

/** ViewType — how a view renders its records. */
export type ViewType = 'TABLE' | 'BOARD' | 'CALENDAR' | 'GALLERY' | 'LIST';

// ─── View filter/sort DSL (mirrors backend AppValueFilterCriteria et al.) ───

/**
 * CustomAppValueOperator — the 9 ops of the backend value DSL; BETWEEN/IN do not exist
 * (an open range is two rows: GTE + LT/LTE). Wire = uppercase enum name.
 */
export type CustomAppValueOperator =
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
export interface CustomAppValueFilter {
  propertyId: string;
  operator: CustomAppValueOperator;
  value?: string | number;
}

/** One sort row — `propertyId` is a property UUID or the reserved 'createdAt'. */
export interface CustomAppValueSort {
  propertyId: string;
  direction: 'asc' | 'desc';
}

/**
 * Typed view config (backend AppViewConfigDto). BOARD requires `groupBy` (a SELECT
 * property id); CALENDAR requires `dateProperty` (a DATE property id); TABLE/LIST/
 * GALLERY forbid both anchors.
 */
export interface CustomAppViewConfig {
  filters?: CustomAppValueFilter[];
  sorts?: CustomAppValueSort[];
  /** BOARD only — SELECT property id whose options become the columns. */
  groupBy?: string;
  /** CALENDAR only — DATE property id used to place records on the grid. */
  dateProperty?: string;
}

// ─── Responses ───

export interface CustomApp {
  id: string;
  name: string;
  description: string | null;
  icon: string | null;
  projectId: string;
  projectName: string | null;
  createdDate: string;
  updatedAt: string;
}

/** Type-scoped property config — backend serializes only the relevant field (NON_NULL). */
export interface CustomAppPropertyConfig {
  /** SELECT: allowed values (required, non-empty, distinct). */
  options?: string[];
  /** RELATION: target customApp id (required). */
  targetCustomAppId?: string;
}

export interface CustomAppProperty {
  id: string;
  customAppId: string;
  name: string;
  type: PropertyType;
  config: CustomAppPropertyConfig | null;
  required: boolean;
  position: number;
}

export interface CustomAppView {
  id: string;
  customAppId: string;
  name: string;
  type: ViewType;
  config: CustomAppViewConfig | null;
  position: number;
}

/** AppDetailResponse — properties/views pre-sorted (position, then name). */
export interface CustomAppDetail extends CustomApp {
  properties: CustomAppProperty[];
  views: CustomAppView[];
}

/** Plan limits (GET /customApps/plan-limits) — values from the backend registry; -1 = unlimited. */
export interface CustomAppPlanLimits {
  planKey: string;
  planName: string;
  maxCustomApps: number;
  maxRecordsPerCustomApp: number;
}

/**
 * Record cell values keyed by property id. JSON scalars per property type:
 * TEXT/SELECT/DATE/USER/RELATION → string, NUMBER → number.
 * Absent key = empty cell; JSON null = cleared value.
 */
export interface CustomAppRecord {
  id: string;
  customAppId: string;
  values: Record<string, string | number | null>;
  createdDate: string;
  updatedAt: string;
  createdBy: string;
}

// ─── Requests ───

export interface CustomAppPropertyRequest {
  name: string;
  /** Required on create AND update (backend @NotNull); on edit it must repeat the existing type. */
  type: PropertyType;
  config?: CustomAppPropertyConfig;
  required?: boolean;
  /** Optional: create appends at max+1, update keeps the current value. */
  position?: number;
}

/** Create/patch payload — only the given keys are touched; null clears (required props reject null). */
export interface CustomAppRecordRequest {
  values: Record<string, string | number | null>;
}

/** View create/update payload: position optional — create appends at max+1, update keeps current. */
export interface CustomAppViewRequest {
  name: string;
  type: ViewType;
  config?: CustomAppViewConfig;
  /** Optional: create appends at max+1, update keeps the current value. */
  position?: number;
}

/**
 * CustomApp create/update payload (full PUT): `icon: null` clears it; `projectId` absent
 * on create → default APPS container, on update moves the customApp (null = unchanged).
 */
export interface CustomAppRequest {
  name: string;
  description?: string;
  icon?: string | null;
  projectId?: string | null;
}
