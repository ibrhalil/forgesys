import { useEffect, useRef, useState } from 'react';
import { useDebouncedValue } from './useDebouncedValue';
import { loadTablePreferences, saveTablePreferences } from './tablePreferences';
import {
  encodeSearchQuery,
  readSearchQueryFromLocation,
  SEARCH_QUERY_PARAM,
  type SearchQueryState,
} from './searchQuery';
import type { FilterCriteria, SearchOrListParams, SortState } from '../types';

export interface UseListPageStateOptions {
  /** Initial sort — the field MUST be in the backend feature's sort whitelist or the request 400s. */
  defaultSort: SortState;
  /** Initial rows-per-page; part of `PAGE_SIZE_OPTIONS`. */
  defaultPageSize?: number;
  /** Search debounce window; the raw input renders immediately, queries key on `q`. */
  debounceMs?: number;
  /** Optional key persisting table preferences (e.g. pageSize) in localStorage. */
  storageKey?: string;
  /**
   * Reflect the committed query state (filters/sort/page/search) into the URL as a
   * single base64url `sq` param (K-55) — shareable view links. Mount precedence:
   * URL > storageKey > defaults. Typing rewrites history (replace), committed
   * changes append (push); back/forward re-hydrates the view. Default false.
   */
  syncUrl?: boolean;
}

/**
 * The list-page scaffold (K-39): page/pageSize/sort/search/filter state with the
 * shared contracts — a new search term, a sort toggle, a page-size change (persisted
 * via storageKey) or a filter change each resets the page to 0. Query with the
 * ready-made `listParams` (`{page, size, sorts: [sort], q, qFields, filters}` —
 * empty optional keys absent; spread scoped legacy params on top). Client-paginated
 * pages (one full response, e.g. permissions) use `useClientPagination` instead and
 * may take only the sort toggle from here.
 */
export function useListPageState({
  defaultSort,
  defaultPageSize = 10,
  debounceMs = 300,
  storageKey,
  syncUrl = false,
}: UseListPageStateOptions) {
  const [urlInitial] = useState(() => (syncUrl ? readSearchQueryFromLocation(window.location.search) : null));

  const [page, setPageState] = useState(urlInitial?.page ?? 0);
  const [pageSize, setPageSizeState] = useState(() => {
    if (urlInitial?.size) return urlInitial.size;
    if (!storageKey) return defaultPageSize;
    return loadTablePreferences(storageKey).pageSize ?? defaultPageSize;
  });
  const [sorts, setSorts] = useState<SortState[]>(
    urlInitial?.sorts?.length ? urlInitial.sorts : [defaultSort],
  );
  const sort: SortState = sorts[0] ?? defaultSort;
  const [search, setSearchState] = useState(urlInitial?.q ?? '');
  const [searchFields, setSearchFieldsState] = useState<string[]>(() => {
    if (urlInitial?.qFields?.length) return urlInitial.qFields;
    if (!storageKey) return [];
    return loadTablePreferences(storageKey).searchFields ?? [];
  });
  const [filters, setFiltersState] = useState<FilterCriteria[]>(urlInitial?.filters ?? []);
  const q = useDebouncedValue(search, debounceMs);

  // URL sync bookkeeping: `interacted` latches ON with the first user change and is
  // reset ONLY by popstate hydration (a write after history navigation would corrupt
  // the forward history). It is never consumed by a write — a debounced q landing
  // late must still write. `mode` picks replace (typing) vs push (committed changes);
  // `hydrating` shields the q-effect's page reset from hydration (the debounced q can
  // land up to `debounceMs` AFTER a popstate re-hydration already restored the page).
  const interactedRef = useRef(!!urlInitial);
  const modeRef = useRef<'replace' | 'push'>('replace');
  const hydratingRef = useRef(!!urlInitial);

  useEffect(() => {
    if (hydratingRef.current) {
      hydratingRef.current = false;
      return;
    }
    setPageState(0);
  }, [q]);

  useEffect(() => {
    if (!syncUrl || !interactedRef.current) return;
    const state: SearchQueryState = {
      v: 1,
      page,
      size: pageSize,
      sorts,
      q: q || undefined,
      qFields: searchFields.length ? searchFields : undefined,
      filters: filters.length ? filters : undefined,
    };
    const blob = encodeSearchQuery(state);
    if (blob === null) return; // over the size cap — leave the URL untouched
    const params = new URLSearchParams(window.location.search);
    params.set(SEARCH_QUERY_PARAM, blob);
    const qs = params.toString();
    const next = qs ? `${window.location.pathname}?${qs}` : window.location.pathname;
    if (next === window.location.pathname + window.location.search) return;
    const mode = modeRef.current;
    modeRef.current = 'replace';
    if (mode === 'push') window.history.pushState(null, '', next);
    else window.history.replaceState(null, '', next);
  }, [syncUrl, page, pageSize, sorts, q, searchFields, filters]);

  // Defaults live in a ref (updated per commit) so the popstate listener subscribes
  // once per syncUrl instead of on every caller's inline-literal identity change.
  const defaultsRef = useRef({ defaultSort, defaultPageSize, storageKey });
  useEffect(() => {
    defaultsRef.current = { defaultSort, defaultPageSize, storageKey };
  });

  useEffect(() => {
    if (!syncUrl) return;
    const onPopState = () => {
      const state = readSearchQueryFromLocation(window.location.search);
      const { defaultSort: sort0, defaultPageSize: size0, storageKey: key0 } = defaultsRef.current;
      interactedRef.current = false;
      hydratingRef.current = true;
      if (state) {
        setPageState(state.page);
        setPageSizeState(state.size);
        setSorts(state.sorts.length ? state.sorts : [sort0]);
        setSearchState(state.q ?? '');
        setSearchFieldsState(state.qFields ?? []);
        setFiltersState(state.filters ?? []);
      } else {
        setPageState(0);
        setPageSizeState(key0 ? loadTablePreferences(key0).pageSize ?? size0 : size0);
        setSorts([sort0]);
        setSearchState('');
        setSearchFieldsState(key0 ? loadTablePreferences(key0).searchFields ?? [] : []);
        setFiltersState([]);
      }
    };
    window.addEventListener('popstate', onPopState);
    return () => window.removeEventListener('popstate', onPopState);
  }, [syncUrl]);

  const markInteraction = (mode: 'replace' | 'push') => {
    interactedRef.current = true;
    modeRef.current = mode;
  };

  const setPage = (next: number) => {
    markInteraction('push');
    setPageState(next);
  };

  const setPageSize = (size: number) => {
    markInteraction('push');
    setPageSizeState(size);
    if (storageKey) {
      saveTablePreferences(storageKey, { pageSize: size });
    }
    setPageState(0);
  };

  const setSearch = (value: string) => {
    hydratingRef.current = false;
    markInteraction('replace');
    setSearchState(value);
  };

  const setSearchFields = (fields: string[]) => {
    markInteraction('push');
    setSearchFieldsState(fields);
    if (storageKey) {
      saveTablePreferences(storageKey, { searchFields: fields });
    }
    setPageState(0);
  };

  const setFilters = (next: FilterCriteria[]) => {
    markInteraction('push');
    setFiltersState(next);
    setPageState(0);
  };

  /**
   * Sort toggle: plain click replaces the single sort (asc→desc→gone on repeat);
   * additive (Shift+click) appends/toggles/removes within a multi-sort chain.
   */
  const toggleSort = (field: string, additive = false) => {
    markInteraction('push');
    setSorts((prev) => {
      const existing = prev.find((s) => s.field === field);
      if (additive) {
        if (!existing) {
          // The untouched default sort is not part of a user-built chain.
          const untouchedDefault = prev.length === 1 && prev[0].field === defaultSort.field;
          return untouchedDefault ? [{ field, dir: 'asc' as const }] : [...prev, { field, dir: 'asc' as const }];
        }
        if (existing.dir === 'asc') {
          return prev.map((s) => (s.field === field ? { field, dir: 'desc' as const } : s));
        }
        return prev.filter((s) => s.field !== field);
      }
      if (existing) {
        return [{ field, dir: existing.dir === 'asc' ? ('desc' as const) : ('asc' as const) }];
      }
      return [{ field, dir: 'asc' as const }];
    });
    setPageState(0);
  };

  /** Applies a saved/shared query as a COMMITTED change (K-55 F7) — same hydration
   *  path as popstate, but marked as an interaction so the URL updates via push. */
  const applySearchQuery = (state: SearchQueryState) => {
    markInteraction('push');
    hydratingRef.current = true;
    setPageState(state.page);
    setPageSizeState(state.size);
    setSorts(state.sorts.length ? state.sorts : [defaultSort]);
    setSearchState(state.q ?? '');
    setSearchFieldsState(state.qFields ?? []);
    setFiltersState(state.filters ?? []);
  };

  // Ready-to-spread list-query params; empty optional keys stay absent, `sorts`
  // serializes to the same repeated `sort=field,dir` wire params as the raw string.
  const listParams: SearchOrListParams = {
    page,
    size: pageSize,
    sorts,
    q: q || undefined,
    qFields: searchFields.length ? searchFields : undefined,
    filters: filters.length ? filters : undefined,
  };

  /** The committed query state in its canonical (URL/API) form — what saving a view persists. */
  const currentQuery: SearchQueryState = {
    v: 1,
    page,
    size: pageSize,
    sorts,
    q: q || undefined,
    qFields: searchFields.length ? searchFields : undefined,
    filters: filters.length ? filters : undefined,
  };

  return {
    page,
    setPage,
    pageSize,
    setPageSize,
    sort,
    sorts,
    toggleSort,
    applySearchQuery,
    currentQuery,
    search,
    setSearch,
    searchFields,
    setSearchFields,
    filters,
    setFilters,
    q,
    listParams,
  };
}
