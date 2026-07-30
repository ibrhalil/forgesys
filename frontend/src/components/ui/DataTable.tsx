import type { ReactNode } from 'react';
import { cn } from '../../lib/cn';
import { useT } from '../../lib/i18n';
import type { SortState } from '../../types';
import { EmptyState } from './EmptyState';
import { Spinner } from './Spinner';

export interface Column<T> {
  key: string;
  header: string;
  render?: (row: T) => ReactNode;
  className?: string;
  /**
   * Backend field this column sorts by (when sortable). Distinct from `key` when the
   * column is a composite (e.g. the users "name" column sorts by `email`). Must be in
   * the feature's backend sort whitelist.
   */
  sortKey?: string;
}

interface DataTableProps<T> {
  columns: Column<T>[];
  data: T[];
  rowKey: (row: T) => string;
  loading?: boolean;
  emptyMessage?: string;
  page: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
  onPageChange: (page: number) => void;
  /**
   * Rows-per-page choices for the footer selector. Omit (or omit
   * {@link onPageSizeChange}) to render no selector — pages that keep a fixed size.
   */
  pageSizeOptions?: readonly number[];
  /** Called with the newly chosen rows-per-page value. Pages should reset to page 0. */
  onPageSizeChange?: (size: number) => void;
  /** Active sort (single-column). Omitted/undefined when the caller has no sorting. */
  sort?: SortState;
  /** Click handler on a sortable header — receives the column's `sortKey`. */
  onSortChange?: (field: string) => void;
  actions?: (row: T) => ReactNode;
  actionsHeader?: string;
  /**
   * Filter/toolbar area rendered inside the card, above the table (below-zero row).
   * Reserved for per-page filters (search, selects, quick actions) — pages adopt it
   * incrementally; nothing changes for callers that omit it.
   */
  toolbar?: ReactNode;
}

/** Above this many choices the footer switches from segments to a compact select. */
const MAX_SEGMENTS = 6;

export function DataTable<T>({
  columns,
  data,
  rowKey,
  loading = false,
  emptyMessage,
  page,
  pageSize,
  totalElements,
  totalPages,
  onPageChange,
  pageSizeOptions,
  onPageSizeChange,
  sort,
  onSortChange,
  actions,
  actionsHeader,
  toolbar,
}: DataTableProps<T>) {
  const { t } = useT();
  const colCount = columns.length + (actions ? 1 : 0);
  const rangeStart = totalElements === 0 ? 0 : page * pageSize + 1;
  const rangeEnd = Math.min((page + 1) * pageSize, totalElements);

  const sortable = (col: Column<T>): boolean => !!col.sortKey && !!onSortChange;

  const headerContent = (col: Column<T>) => {
    if (!sortable(col)) return col.header;
    const active = !!sort && sort.field === col.sortKey;
    return (
      <button
        type="button"
        onClick={() => onSortChange?.(col.sortKey!)}
        className="group inline-flex items-center gap-1 uppercase tracking-wide transition-colors hover:text-main"
        title={col.header}
      >
        {col.header}
        <span
          aria-hidden
          className={cn(
            'text-[10px] leading-none',
            active ? 'text-accent' : 'text-muted/50 group-hover:text-muted',
          )}
        >
          {active && sort ? (sort.dir === 'asc' ? '▲' : '▼') : '▾'}
        </span>
      </button>
    );
  };

  const ariaSort = (col: Column<T>): 'ascending' | 'descending' | undefined => {
    if (!sortable(col) || !sort || sort.field !== col.sortKey) {
      return undefined;
    }
    return sort.dir === 'asc' ? 'ascending' : 'descending';
  };

  return (
    <div className="overflow-hidden rounded-xl border border-glass bg-surface shadow-sm shadow-black/[0.03]">
      {toolbar && (
        <div className="flex flex-wrap items-end gap-3 border-b border-glass bg-bg/40 px-4 py-3">
          {toolbar}
        </div>
      )}

      <div className="overflow-x-auto">
        <table className="w-full border-collapse text-sm">
          <thead>
            <tr className="border-b border-glass bg-bg/40">
              {columns.map((col) => (
                <th
                  key={col.key}
                  aria-sort={ariaSort(col)}
                  className={cn('px-4 py-3 text-left text-xs font-semibold uppercase tracking-wide text-muted', col.className)}
                >
                  {headerContent(col)}
                </th>
              ))}
              {actions && <th className="px-4 py-3 text-right text-xs font-semibold uppercase tracking-wide text-muted">{actionsHeader}</th>}
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan={colCount} className="px-4 py-16 text-center">
                  <Spinner className="border-muted/40 border-t-accent" />
                </td>
              </tr>
            ) : data.length === 0 ? (
              <tr>
                <td colSpan={colCount}>
                  <EmptyState message={emptyMessage ?? t('table.noRecords')} />
                </td>
              </tr>
            ) : (
              data.map((row) => (
                <tr key={rowKey(row)} className="border-b border-glass/60 transition-colors last:border-0 hover:bg-accent/[0.04]">
                  {columns.map((col) => (
                    <td key={col.key} className={cn('px-4 py-3.5 text-main', col.className)}>
                      {col.render ? col.render(row) : String((row as Record<string, unknown>)[col.key] ?? '')}
                    </td>
                  ))}
                  {actions && <td className="px-4 py-3.5 text-right">{actions(row)}</td>}
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      <div className="flex flex-wrap items-center justify-between gap-4 border-t border-glass bg-bg/40 px-4 py-3">
        {/* Rows-per-page: minimal segments while few options; a ghost native select when
            the option list grows, so an extended PAGE_SIZE_OPTIONS never crowds the footer. */}
        {pageSizeOptions && onPageSizeChange && (
          pageSizeOptions.length <= MAX_SEGMENTS ? (
            <div role="group" aria-label={t('table.rowsPerPage')} className="flex items-center gap-0.5">
              {pageSizeOptions.map((n) => (
                <button
                  key={n}
                  type="button"
                  aria-pressed={pageSize === n}
                  onClick={() => n !== pageSize && onPageSizeChange(n)}
                  className={cn(
                    'rounded-md px-2 py-1 text-xs transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-accent/60',
                    pageSize === n
                      ? 'font-semibold text-accent'
                      : 'text-muted hover:text-main',
                  )}
                >
                  {n}
                </button>
              ))}
            </div>
          ) : (
            <label className="flex items-center gap-1.5 text-xs text-muted">
              {t('table.rowsPerPage')}
              <select
                value={pageSize}
                onChange={(e) => onPageSizeChange(Number(e.target.value))}
                className="cursor-pointer rounded-md bg-transparent py-0.5 pl-1 pr-5 text-xs font-semibold text-accent transition-colors hover:text-main focus:outline-none focus-visible:ring-2 focus-visible:ring-accent/60"
              >
                {pageSizeOptions.map((n) => (
                  <option key={n} value={n}>{n}</option>
                ))}
              </select>
            </label>
          )
        )}
        <div className="flex items-center gap-3">
          <span className="text-xs text-muted">
            {totalElements === 0
              ? t('table.noItems')
              : t('table.showingRange', { from: rangeStart, to: rangeEnd, total: totalElements })}
          </span>
          <div className="flex items-center gap-2">
            <button
              onClick={() => onPageChange(page - 1)}
              disabled={page === 0}
              className="rounded-md border border-glass bg-surface px-3 py-1 text-xs text-main transition-colors hover:bg-accent/5 hover:border-accent/30 disabled:opacity-40 disabled:hover:bg-surface disabled:hover:border-glass"
            >
              {t('table.prev')}
            </button>
            <span className="text-xs text-muted">
              {t('table.page', { current: totalPages === 0 ? 0 : page + 1, total: totalPages })}
            </span>
            <button
              onClick={() => onPageChange(page + 1)}
              disabled={page >= totalPages - 1}
              className="rounded-md border border-glass bg-surface px-3 py-1 text-xs text-main transition-colors hover:bg-accent/5 hover:border-accent/30 disabled:opacity-40 disabled:hover:bg-surface disabled:hover:border-glass"
            >
              {t('table.next')}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
