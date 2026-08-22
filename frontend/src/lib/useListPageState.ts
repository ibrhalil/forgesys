import { useEffect, useState } from 'react';
import { useDebouncedValue } from './useDebouncedValue';
import type { SortState } from '../types';

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
}

/**
 * The list-page scaffold (K-39): page/pageSize/sort/search state with the shared
 * contracts every server-side list page repeated inline before this hook:
 *
 * - search is debounced (`q`), and a new term resets the page to 0;
 * - sorting toggles asc/desc on the same field, switches to asc on a new field,
 *   and resets the page to 0;
 * - changing the rows-per-page resets the page to 0.
 *
 * Pages wire DataTable directly: `onPageSizeChange={setPageSize}`,
 * `onSortChange={toggleSort}`, `onPageChange={setPage}`,
 * `toolbar={<SearchInput value={search} onChange={setSearch} …/>}` and query with
 * `page`/`size: pageSize`/`sorts: [sort]` (or the legacy `sort` string)/`q`.
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
}: UseListPageStateOptions) {
  const [page, setPage] = useState(0);
  const [pageSize, setPageSizeState] = useState(defaultPageSize);
  const [sort, setSort] = useState<SortState>(defaultSort);
  const [search, setSearch] = useState('');
  const q = useDebouncedValue(search, debounceMs);

  // A new search term invalidates the current page position.
  useEffect(() => {
    setPage(0);
  }, [q]);

  const setPageSize = (size: number) => {
    setPageSizeState(size);
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

  return { page, setPage, pageSize, setPageSize, sort, toggleSort, search, setSearch, q };
}
