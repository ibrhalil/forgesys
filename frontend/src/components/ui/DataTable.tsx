import type { ReactNode } from 'react';
import { cn } from '../../lib/cn';
import { EmptyState } from './EmptyState';

export interface Column<T> {
  key: string;
  header: string;
  render?: (row: T) => ReactNode;
  className?: string;
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
  actions?: (row: T) => ReactNode;
  actionsHeader?: string;
}

export function DataTable<T>({
  columns,
  data,
  rowKey,
  loading = false,
  emptyMessage = 'No records',
  page,
  pageSize,
  totalElements,
  totalPages,
  onPageChange,
  actions,
  actionsHeader = '',
}: DataTableProps<T>) {
  const colCount = columns.length + (actions ? 1 : 0);
  const rangeStart = totalElements === 0 ? 0 : page * pageSize + 1;
  const rangeEnd = Math.min((page + 1) * pageSize, totalElements);

  return (
    <div className="overflow-hidden rounded-xl border border-glass bg-surface backdrop-blur-md">
      <div className="overflow-x-auto">
        <table className="w-full border-collapse text-sm">
          <thead>
            <tr className="border-b border-glass">
              {columns.map((col) => (
                <th
                  key={col.key}
                  className={cn('px-4 py-3 text-left text-xs font-semibold uppercase tracking-wide text-muted', col.className)}
                >
                  {col.header}
                </th>
              ))}
              {actions && <th className="px-4 py-3 text-right text-xs font-semibold uppercase tracking-wide text-muted">{actionsHeader}</th>}
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan={colCount} className="px-4 py-16 text-center">
                  <span className="inline-block h-6 w-6 animate-spin rounded-full border-2 border-muted/40 border-t-accent" />
                </td>
              </tr>
            ) : data.length === 0 ? (
              <tr>
                <td colSpan={colCount}>
                  <EmptyState message={emptyMessage} />
                </td>
              </tr>
            ) : (
              data.map((row) => (
                <tr key={rowKey(row)} className="border-b border-glass/60 transition-colors last:border-0 hover:bg-white/[0.03]">
                  {columns.map((col) => (
                    <td key={col.key} className={cn('px-4 py-3 text-main', col.className)}>
                      {col.render ? col.render(row) : String((row as Record<string, unknown>)[col.key] ?? '')}
                    </td>
                  ))}
                  {actions && <td className="px-4 py-3 text-right">{actions(row)}</td>}
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      <div className="flex items-center justify-between gap-4 border-t border-glass px-4 py-3">
        <span className="text-xs text-muted">
          {totalElements === 0 ? 'No items' : `Showing ${rangeStart}–${rangeEnd} of ${totalElements}`}
        </span>
        <div className="flex items-center gap-2">
          <button
            onClick={() => onPageChange(page - 1)}
            disabled={page === 0}
            className="rounded-md border border-glass px-3 py-1 text-xs text-main transition-colors hover:bg-white/5 disabled:opacity-40 disabled:hover:bg-transparent"
          >
            Prev
          </button>
          <span className="text-xs text-muted">
            Page {totalPages === 0 ? 0 : page + 1} / {totalPages}
          </span>
          <button
            onClick={() => onPageChange(page + 1)}
            disabled={page >= totalPages - 1}
            className="rounded-md border border-glass px-3 py-1 text-xs text-main transition-colors hover:bg-white/5 disabled:opacity-40 disabled:hover:bg-transparent"
          >
            Next
          </button>
        </div>
      </div>
    </div>
  );
}
