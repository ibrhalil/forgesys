import { useEffect, useState } from 'react';
import { useDebouncedValue } from './useDebouncedValue';
import { loadTablePreferences, saveTablePreferences } from './tablePreferences';
import type { FilterCriteria, SortState } from '../types';

export interface UseListPageStateOptions {
  /**
   * Initial sort. The field MUST be in the backend feature's sort whitelist
   * (SortGuard) or the request 400s — see the page's `Column.sortKey` docs.
   */
  defaultSort: SortState;
  /** Initial rows-per-page; part of `PAGE_SIZE_OPTIONS`. */
  defaultPageSize?: number;
  /** Search debounce window; the raw input renders immediately, queries key on `q`. */
  debounceMs?: number;
  /**
   * Optional unique key to persist table preferences (e.g. pageSize) in localStorage.
   */
  storageKey?: string;
}

/**
 * The list-page scaffold (K-39): page/pageSize/sort/search/filter state with the
 * shared contracts every server-side list page repeated inline before this hook:
 *
 * - search is debounced (`q`), and a new term resets the page to 0;
 * - sorting toggles asc/desc on the same field, switches to asc on a new field,
 *   and resets the page to 0;
 * - changing the rows-per-page resets the page to 0 and persists preference if storageKey is given;
 * - changing the structured filters (K-49 column filters) resets the page to 0.
 *
 * Pages wire DataTable directly: `onPageSizeChange={setPageSize}`,
 * `onSortChange={toggleSort}`, `onPageChange={setPage}`,
 * `filters={filters}` / `onFiltersChange={setFilters}`,
 * `toolbar={<SearchInput value={search} onChange={setSearch} …/>}` and query with
 * `page`/`size: pageSize`/`sorts: [sort]` (or the legacy `sort` string)/`q`/
 * `qFields: searchFields` — or the filter-engine `POST /search` body carrying
 * `filters` when any is active.
 *
 * Client-paginated pages (full list in one response, e.g. permissions) manage
 * their own page state via `useClientPagination` — they may still use this hook
 * for the sort toggle only, ignoring the unused page/search state (it is inert
 * when never wired).
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

  // A new search term invalidates the current page position.
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
  };
}
