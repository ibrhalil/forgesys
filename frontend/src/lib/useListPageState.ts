import { useEffect, useState } from 'react';
import { useDebouncedValue } from './useDebouncedValue';
import { loadTablePreferences, saveTablePreferences } from './tablePreferences';
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
}: UseListPageStateOptions) {
  const [page, setPage] = useState(0);
  const [pageSize, setPageSizeState] = useState(() => {
    if (!storageKey) return defaultPageSize;
    return loadTablePreferences(storageKey).pageSize ?? defaultPageSize;
  });
  const [sort, setSort] = useState<SortState>(defaultSort);
  const [search, setSearch] = useState('');
  const [searchFields, setSearchFieldsState] = useState<string[]>(() => {
    if (!storageKey) return [];
    return loadTablePreferences(storageKey).searchFields ?? [];
  });
  const [filters, setFiltersState] = useState<FilterCriteria[]>([]);
  const q = useDebouncedValue(search, debounceMs);

  useEffect(() => {
    setPage(0);
  }, [q]);

  const setPageSize = (size: number) => {
    setPageSizeState(size);
    if (storageKey) {
      saveTablePreferences(storageKey, { pageSize: size });
    }
    setPage(0);
  };

  const setSearchFields = (fields: string[]) => {
    setSearchFieldsState(fields);
    if (storageKey) {
      saveTablePreferences(storageKey, { searchFields: fields });
    }
    setPage(0);
  };

  const setFilters = (next: FilterCriteria[]) => {
    setFiltersState(next);
    setPage(0);
  };

  const toggleSort = (field: string) => {
    setSort((prev) =>
      prev.field === field
        ? { field, dir: prev.dir === 'asc' ? 'desc' : 'asc' }
        : { field, dir: 'asc' },
    );
    setPage(0);
  };

  // Ready-to-spread list-query params; empty optional keys stay absent, `sorts`
  // serializes to the same repeated `sort=field,dir` wire params as the raw string.
  const listParams: SearchOrListParams = {
    page,
    size: pageSize,
    sorts: [sort],
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
    toggleSort,
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
