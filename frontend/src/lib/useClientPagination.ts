import { useEffect, useMemo, useState } from 'react';
import { loadTablePreferences, saveTablePreferences } from './tablePreferences';

/**
 * Client-side pagination over a locally held list — the DataTable-footer experience
 * (rows-per-page + page navigation) for pages whose backend returns the full list in
 * one response (e.g. permissions). Mirrors the server-side page contract
 * (`page`/`pageSize`/`totalElements`/`totalPages`) so DataTable wiring is identical.
 */
export function useClientPagination<T>(items: T[], defaultPageSize = 10, storageKey?: string) {
  const [page, setPage] = useState(0);
  const [pageSize, setPageSizeState] = useState(() => {
    if (!storageKey) return defaultPageSize;
    return loadTablePreferences(storageKey).pageSize ?? defaultPageSize;
  });

  const totalElements = items.length;
  const totalPages = Math.max(1, Math.ceil(totalElements / pageSize));

  // Clamp when the source shrinks (filter/delete) so the view never sits past the last page.
  const effectivePage = Math.min(page, totalPages - 1);
  useEffect(() => {
    if (page !== effectivePage) setPage(effectivePage);
  }, [page, effectivePage]);

  const setPageSize = (size: number) => {
    setPageSizeState(size);
    if (storageKey) {
      saveTablePreferences(storageKey, { pageSize: size });
    }
    setPage(0);
  };

  const paged = useMemo(
    () => items.slice(effectivePage * pageSize, (effectivePage + 1) * pageSize),
    [items, effectivePage, pageSize],
  );

  return { paged, page: effectivePage, setPage, pageSize, setPageSize, totalElements, totalPages };
}
