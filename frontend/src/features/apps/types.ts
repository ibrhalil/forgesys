// ─── Enums (wire = uppercase Java enum name) ───

/** PropertyType (backend rejects FORMULA on create — deferred type). */
export type PropertyType = 'TEXT' | 'NUMBER' | 'SELECT' | 'DATE' | 'USER' | 'RELATION' | 'FORMULA';

/** ViewType — view CRUD/renderers land in a later part; typed for the detail response. */
export type ViewType = 'TABLE' | 'BOARD' | 'CALENDAR' | 'GALLERY' | 'LIST';

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
  config: Record<string, unknown> | null;
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
