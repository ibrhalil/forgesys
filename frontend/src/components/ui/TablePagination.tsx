import { cn } from '../../lib/cn';
import { useT } from '../../lib/i18n';
import { META_MONO } from './styles';

interface TablePaginationProps {
  page: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
  onPageChange: (page: number) => void;
  /**
   * Rows-per-page choices for the selector. Omit (or omit {@link onPageSizeChange})
   * to render no selector — surfaces that keep a fixed page size.
   */
  pageSizeOptions?: readonly number[];
  /** Called with the newly chosen rows-per-page value. Pages should reset to page 0. */
  onPageSizeChange?: (size: number) => void;
}

/** Above this many choices the footer switches from segments to a compact select. */
const MAX_SEGMENTS = 6;

/**
 * The shared table pagination footer: showing-range text, prev/page/next controls
 * and (optionally) the rows-per-page selector — extracted verbatim from DataTable so
 * non-table surfaces (e.g. the record gallery grid) get the identical footer UX.
 * Rows-per-page renders only when both {@link pageSizeOptions} and
 * {@link onPageSizeChange} are provided.
 */
export function TablePagination({
  page,
  pageSize,
  totalElements,
  totalPages,
  onPageChange,
  pageSizeOptions,
  onPageSizeChange,
}: TablePaginationProps) {
  const { t } = useT();
  const rangeStart = totalElements === 0 ? 0 : page * pageSize + 1;
  const rangeEnd = Math.min((page + 1) * pageSize, totalElements);

  return (
    <div className="flex flex-wrap items-center justify-between gap-4 border-t border-glass bg-bg/40 px-4 py-3">
      {/* Rows-per-page: minimal segments while few options; a ghost native select when
          the option list grows, so an extended PAGE_SIZE_OPTIONS never crowds the footer. */}
      {pageSizeOptions && onPageSizeChange ? (
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
      ) : null}
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
            className="rounded-md border border-glass bg-surface px-3 py-1 text-xs text-main transition-colors hover:bg-accent/5 hover:border-accent/30 focus:outline-none focus-visible:ring-2 focus-visible:ring-accent/60 disabled:opacity-50 disabled:hover:bg-surface disabled:hover:border-glass"
          >
            {t('table.prev')}
          </button>
          <span className={META_MONO}>
            {t('table.page', { current: totalPages === 0 ? 0 : page + 1, total: totalPages })}
          </span>
          <button
            onClick={() => onPageChange(page + 1)}
            disabled={page >= totalPages - 1}
            className="rounded-md border border-glass bg-surface px-3 py-1 text-xs text-main transition-colors hover:bg-accent/5 hover:border-accent/30 focus:outline-none focus-visible:ring-2 focus-visible:ring-accent/60 disabled:opacity-50 disabled:hover:bg-surface disabled:hover:border-glass"
          >
            {t('table.next')}
          </button>
        </div>
      </div>
    </div>
  );
}
