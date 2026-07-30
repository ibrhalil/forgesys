import { useEffect, useMemo, useState } from 'react';

/**
 * Client-side pagination over a locally held list — the DataTable-footer experience
 * (rows-per-page + page navigation) for pages whose backend returns the full list in
 * one response (e.g. permissions). Mirrors the server-side page contract
 * (`page`/`pageSize`/`totalElements`/`totalPages`) so DataTable wiring is identical.
 */
export function useClientPagination<T>(items: T[], defaultPageSize = 10) {
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(defaultPageSize);

  const totalElements = items.length;
  const totalPages = Math.max(1, Math.ceil(totalElements / pageSize));

  // Clamp when the source shrinks (filter/delete) so the view never sits past the last page.
  const effectivePage = Math.min(page, totalPages - 1);
  useEffect(() => {
    if (page !== effectivePage) setPage(effectivePage);
  }, [page, effectivePage]);

  const paged = useMemo(
    () => items.slice(effectivePage * pageSize, (effectivePage + 1) * pageSize),
    [items, effectivePage, pageSize],
  );

  return { paged, page: effectivePage, setPage, pageSize, setPageSize, totalElements, totalPages };
}
