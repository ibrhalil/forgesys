import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import {
  LuColumns3,
  LuDownload,
  LuLayoutGrid,
  LuList,
  LuRefreshCw,
  LuRows3,
  LuSlidersHorizontal,
  LuTable2,
} from 'react-icons/lu';
import { cn } from '../../lib/cn';
import { useT } from '../../lib/i18n';
import type { IconType } from 'react-icons';
import {
  loadTablePreferences,
  saveTablePreferences,
  type TableViewMode,
} from '../../lib/tablePreferences';
import type { FilterCriteria, SortState } from '../../types';
import { Badge } from './Badge';
import { ColumnFilterButton, type ColumnFilterSpec } from './ColumnFilterButton';
import { EmptyState } from './EmptyState';
import { Spinner } from './Spinner';
import { TablePagination } from './TablePagination';
import { MICRO_LABEL } from './styles';

export type { TableViewMode };

export interface Column<T> {
  key: string;
  header: string;
  render?: (row: T) => ReactNode;
  className?: string;
  /**
   * Backend field this column sorts by; distinct from `key` on composite columns
   * (users "name" sorts by `email`). Must be in the feature's sort whitelist or the
   * request 400s.
   */
  sortKey?: string;
  /** Structured column filter (K-49) — renders a popover when the table receives `filters`/`onFiltersChange`. */
  filter?: ColumnFilterSpec;
  /** Whether the user can hide this column (default true; false for essential primary columns). */
  hideable?: boolean;
}

export type TableDensity = 'compact' | 'normal' | 'relaxed';

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
  /** Rows-per-page choices; omit (or omit {@link onPageSizeChange}) for fixed-size pages. */
  pageSizeOptions?: readonly number[];
  /** Called with the newly chosen rows-per-page value. Pages should reset to page 0. */
  onPageSizeChange?: (size: number) => void;
  /** Active sort (single-column). Omitted/undefined when the caller has no sorting. */
  sort?: SortState;
  /** Click handler on a sortable header — receives the column's `sortKey`. */
  onSortChange?: (field: string) => void;
  /** Active structured filter clauses (K-49) — omitted means no column renders a filter trigger. */
  filters?: FilterCriteria[];
  /** Called with the full clause list after a column filter is applied or cleared. */
  onFiltersChange?: (filters: FilterCriteria[]) => void;
  actions?: (row: T) => ReactNode;
  actionsHeader?: string;
  /** Filter/toolbar area rendered above the table — per-page search, selects, quick actions. */
  toolbar?: ReactNode;
  /** localStorage key persisting table preferences (hidden columns, density, viewMode). */
  storageKey?: string;
  /** Whether view customization is enabled; defaults to true when storageKey is provided. */
  customizableColumns?: boolean;
  /** Export handler; omitted renders the export options disabled ("Coming soon"). */
  onExport?: (format: 'csv' | 'excel' | 'pdf') => void;
  /** Manual refresh handler; omitted renders the refresh options disabled ("Coming soon"). */
  onRefresh?: () => void;
  /** Custom toolbar action buttons rendered alongside the table controls. */
  tableTools?: ReactNode;
  /** Supported view modes; more than one renders the view switcher toggle. */
  viewModes?: TableViewMode[];
  /** Controlled view mode; omitted = internal state persisted via storageKey. */
  viewMode?: TableViewMode;
  /** Called when the view mode changes. */
  onViewModeChange?: (mode: TableViewMode) => void;
  /** Custom 'card' renderer; omitted = structured auto-card from visible columns. */
  cardRender?: (row: T) => ReactNode;
  /** Custom 'list' renderer. */
  listRender?: (row: T) => ReactNode;
  /** Empty-state icon (defaults to EmptyState's generic folder). */
  emptyIcon?: IconType;
}


type SettingsTab = 'columns' | 'density' | 'export' | 'refresh';

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
  filters,
  onFiltersChange,
  actions,
  actionsHeader,
  toolbar,
  storageKey,
  customizableColumns = true,
  onExport,
  onRefresh,
  tableTools,
  viewModes,
  viewMode: controlledViewMode,
  onViewModeChange,
  cardRender,
  listRender,
  emptyIcon,
}: DataTableProps<T>) {
  const { t } = useT();

  const [hiddenColumns, setHiddenColumns] = useState<string[]>(() => {
    if (!storageKey) return [];
    return loadTablePreferences(storageKey).hiddenColumns ?? [];
  });
  const [density, setDensity] = useState<TableDensity>(() => {
    if (!storageKey) return 'normal';
    return (loadTablePreferences(storageKey).density as TableDensity) ?? 'normal';
  });
  const [internalViewMode, setInternalViewMode] = useState<TableViewMode>(() => {
    if (!storageKey) return viewModes?.[0] ?? 'table';
    return (loadTablePreferences(storageKey).viewMode as TableViewMode) ?? viewModes?.[0] ?? 'table';
  });

  const activeViewMode = controlledViewMode ?? internalViewMode;

  const [showSettingsMenu, setShowSettingsMenu] = useState(false);
  const [activeTab, setActiveTab] = useState<SettingsTab>('columns');

  const menuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!showSettingsMenu) return;

    const onPointerDown = (e: MouseEvent | TouchEvent) => {
      const target = e.target as Node;
      if (menuRef.current && !menuRef.current.contains(target)) {
        setShowSettingsMenu(false);
      }
    };
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        setShowSettingsMenu(false);
      }
    };

    document.addEventListener('mousedown', onPointerDown);
    document.addEventListener('touchstart', onPointerDown);
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('mousedown', onPointerDown);
      document.removeEventListener('touchstart', onPointerDown);
      document.removeEventListener('keydown', onKeyDown);
    };
  }, [showSettingsMenu]);

  const visibleColumns = useMemo(() => {
    return columns.filter((col) => !hiddenColumns.includes(col.key));
  }, [columns, hiddenColumns]);

  const colCount = visibleColumns.length + (actions ? 1 : 0);

  const isCustomizationEnabled = !!storageKey && customizableColumns;
  const hasActivePreferences =
    hiddenColumns.length > 0 || density !== 'normal' || (viewModes && activeViewMode !== (viewModes[0] ?? 'table'));

  const toggleColumnVisibility = (colKey: string) => {
    const isHidden = hiddenColumns.includes(colKey);
    let nextHidden: string[];
    if (isHidden) {
      nextHidden = hiddenColumns.filter((k) => k !== colKey);
    } else {
      if (visibleColumns.length <= 1) return; // Keep at least one column visible
      nextHidden = [...hiddenColumns, colKey];
    }
    setHiddenColumns(nextHidden);
    if (storageKey) {
      saveTablePreferences(storageKey, { hiddenColumns: nextHidden });
    }
  };

  const handleResetColumns = () => {
    setHiddenColumns([]);
    if (storageKey) {
      saveTablePreferences(storageKey, { hiddenColumns: [] });
    }
  };

  const handleDensityChange = (newDensity: TableDensity) => {
    setDensity(newDensity);
    if (storageKey) {
      saveTablePreferences(storageKey, { density: newDensity });
    }
  };

  const handleViewModeChange = (newMode: TableViewMode) => {
    setInternalViewMode(newMode);
    if (storageKey) {
      saveTablePreferences(storageKey, { viewMode: newMode });
    }
    onViewModeChange?.(newMode);
  };

  const sortable = (col: Column<T>): boolean => !!col.sortKey && !!onSortChange;

  const columnFilterEnabled = (col: Column<T>): boolean =>
    !!col.filter && filters !== undefined && !!onFiltersChange;

  const activeFilterFor = (col: Column<T>): FilterCriteria | undefined =>
    filters?.find((f) => f.field === col.filter?.field);

  const handleFilterChange = (col: Column<T>, criteria: FilterCriteria | null) => {
    if (!col.filter) return;
    const rest = (filters ?? []).filter((f) => f.field !== col.filter!.field);
    onFiltersChange?.(criteria ? [...rest, criteria] : rest);
  };

  const headerContent = (col: Column<T>) => {
    const label =
      !sortable(col) ? (
        col.header
      ) : (
        (() => {
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
        })()
      );
    if (!columnFilterEnabled(col)) return label;
    return (
      <span className="inline-flex items-center gap-1.5">
        {label}
        <ColumnFilterButton
          spec={col.filter!}
          header={col.header}
          active={activeFilterFor(col)}
          onChange={(criteria) => handleFilterChange(col, criteria)}
        />
      </span>
    );
  };

  const ariaSort = (col: Column<T>): 'ascending' | 'descending' | undefined => {
    if (!sortable(col) || !sort || sort.field !== col.sortKey) {
      return undefined;
    }
    return sort.dir === 'asc' ? 'ascending' : 'descending';
  };

  const thPadding =
    density === 'compact' ? 'px-3 py-2 text-[11px]' : density === 'relaxed' ? 'px-5 py-3.5 text-sm' : 'px-4 py-3 text-xs';
  const tdPadding =
    density === 'compact' ? 'px-3 py-2 text-xs' : density === 'relaxed' ? 'px-5 py-4 text-base' : 'px-4 py-3.5 text-sm';

  return (
    <div className="rounded-lg border border-glass bg-surface shadow-sm shadow-black/[0.03]">
      {(toolbar || isCustomizationEnabled || tableTools) && (
        <div className="flex flex-wrap items-center justify-between gap-3 rounded-t-lg border-b border-glass bg-bg/40 px-4 py-2.5">
          <div className="flex flex-1 flex-wrap items-center gap-3">
            {toolbar}
          </div>

          <div className="flex items-center gap-2">
            {viewModes && viewModes.length > 1 && (
              <div
                className="flex items-center rounded-md border border-glass bg-bg/50 p-0.5"
                role="group"
                aria-label={t('table.viewMode')}
              >
                {viewModes.map((mode) => {
                  const isSelected = activeViewMode === mode;
                  const Icon = mode === 'table' ? LuTable2 : mode === 'card' ? LuLayoutGrid : LuList;
                  const label =
                    mode === 'table'
                      ? t('table.viewModeTable')
                      : mode === 'card'
                      ? t('table.viewModeCard')
                      : t('table.viewModeList');
                  return (
                    <button
                      key={mode}
                      type="button"
                      onClick={() => handleViewModeChange(mode)}
                      className={cn(
                        'flex h-7 items-center gap-1.5 rounded-md px-2 text-xs font-medium transition-colors',
                        isSelected
                          ? 'bg-surface font-semibold text-accent shadow-xs'
                          : 'text-muted hover:text-main',
                      )}
                      title={label}
                      aria-label={label}
                      aria-pressed={isSelected}
                    >
                      <Icon className="h-3.5 w-3.5" />
                      <span className="hidden sm:inline">{label}</span>
                    </button>
                  );
                })}
              </div>
            )}

            {tableTools}

            {isCustomizationEnabled && (
              <div className="relative" ref={menuRef}>
                <button
                  type="button"
                  onClick={() => setShowSettingsMenu((v) => !v)}
                  className={cn(
                    'relative inline-flex h-8 w-8 items-center justify-center rounded-lg border border-glass bg-surface text-muted transition-colors hover:border-accent/40 hover:bg-accent/5 hover:text-main focus:outline-none focus-visible:ring-2 focus-visible:ring-accent/60',
                    showSettingsMenu && 'border-accent/50 bg-accent/5 text-accent',
                  )}
                  title={t('table.settings')}
                  aria-label={t('table.settings')}
                  aria-expanded={showSettingsMenu}
                >
                  <LuSlidersHorizontal className="h-4 w-4" />
                  {hasActivePreferences && (
                    <span className="absolute -right-0.5 -top-0.5 h-2 w-2 rounded-full bg-accent ring-2 ring-surface" />
                  )}
                </button>

                {showSettingsMenu && (
                  <div className="absolute right-0 top-full z-60 mt-1.5 w-72 overflow-hidden rounded-lg border border-glass bg-surface shadow-lg shadow-black/10">
                    <div className="flex border-b border-glass bg-bg/30 p-1">
                      <button
                        type="button"
                        onClick={() => setActiveTab('columns')}
                        className={cn(
                          'flex flex-1 items-center justify-center gap-1.5 rounded-lg py-1.5 text-xs font-medium transition-colors',
                          activeTab === 'columns'
                            ? 'bg-surface text-accent shadow-xs'
                            : 'text-muted hover:text-main',
                        )}
                        title={t('table.columns')}
                      >
                        <LuColumns3 className="h-3.5 w-3.5" />
                        <span>{t('table.columns')}</span>
                      </button>

                      <button
                        type="button"
                        onClick={() => setActiveTab('density')}
                        className={cn(
                          'flex flex-1 items-center justify-center gap-1.5 rounded-lg py-1.5 text-xs font-medium transition-colors',
                          activeTab === 'density'
                            ? 'bg-surface text-accent shadow-xs'
                            : 'text-muted hover:text-main',
                        )}
                        title={t('table.density')}
                      >
                        <LuRows3 className="h-3.5 w-3.5" />
                        <span>{t('table.density')}</span>
                      </button>

                      <button
                        type="button"
                        onClick={() => setActiveTab('export')}
                        className={cn(
                          'flex flex-1 items-center justify-center gap-1.5 rounded-lg py-1.5 text-xs font-medium transition-colors',
                          activeTab === 'export'
                            ? 'bg-surface text-accent shadow-xs'
                            : 'text-muted hover:text-main',
                        )}
                        title={t('table.export')}
                      >
                        <LuDownload className="h-3.5 w-3.5" />
                        <span>{t('table.export')}</span>
                      </button>

                      <button
                        type="button"
                        onClick={() => setActiveTab('refresh')}
                        className={cn(
                          'flex flex-1 items-center justify-center gap-1.5 rounded-lg py-1.5 text-xs font-medium transition-colors',
                          activeTab === 'refresh'
                            ? 'bg-surface text-accent shadow-xs'
                            : 'text-muted hover:text-main',
                        )}
                        title={t('table.autoRefresh')}
                      >
                        <LuRefreshCw className="h-3.5 w-3.5" />
                        <span>{t('table.autoRefresh')}</span>
                      </button>
                    </div>

                    {/* Tab Body */}
                    <div className="p-3">
                      {activeTab === 'columns' && (
                        <div className="space-y-3">
                          <div className="flex items-center justify-between border-b border-glass pb-1.5">
                            <span className="text-xs font-semibold text-main">
                              {t('table.customizeColumns')}
                            </span>
                            {hiddenColumns.length > 0 && (
                              <button
                                type="button"
                                onClick={handleResetColumns}
                                className="text-[11px] font-medium text-accent hover:underline"
                              >
                                {t('table.resetColumns')}
                              </button>
                            )}
                          </div>

                          <div className="max-h-48 space-y-1 overflow-y-auto pr-1">
                            {columns.map((col) => {
                              const isHideable = col.hideable !== false;
                              const isChecked = !hiddenColumns.includes(col.key);

                              return (
                                <label
                                  key={col.key}
                                  className={cn(
                                    'flex select-none items-center justify-between gap-2 rounded-md px-2 py-1 text-xs transition-colors',
                                    isHideable
                                      ? 'cursor-pointer hover:bg-main/5'
                                      : 'cursor-not-allowed opacity-60',
                                  )}
                                >
                                  <div className="flex min-w-0 items-center gap-2">
                                    <input
                                      type="checkbox"
                                      checked={isChecked}
                                      disabled={!isHideable}
                                      onChange={() => isHideable && toggleColumnVisibility(col.key)}
                                      className="accent-accent"
                                    />
                                    <span className="truncate text-main">{col.header}</span>
                                  </div>

                                  {!isHideable && (
                                    <Badge tone="muted" className="shrink-0 text-[10px]">
                                      {t('table.primary')}
                                    </Badge>
                                  )}
                                </label>
                              );
                            })}
                          </div>
                        </div>
                      )}

                      {activeTab === 'density' && (
                        <div className="space-y-2">
                          <span className="mb-1 block text-xs font-semibold text-main">
                            {t('table.density')}
                          </span>
                          {(
                            [
                              { mode: 'compact', label: t('table.densityCompact') },
                              { mode: 'normal', label: t('table.densityNormal') },
                              { mode: 'relaxed', label: t('table.densityRelaxed') },
                            ] as const
                          ).map((item) => (
                            <button
                              key={item.mode}
                              type="button"
                              onClick={() => handleDensityChange(item.mode)}
                              className={cn(
                                'flex w-full items-center justify-between rounded-md border border-glass px-2.5 py-1.5 text-xs transition-colors',
                                density === item.mode
                                  ? 'border-accent/40 bg-accent/10 font-semibold text-accent'
                                  : 'bg-surface text-main hover:bg-main/5',
                              )}
                            >
                              <span>{item.label}</span>
                              {density === item.mode && (
                                <span className="h-1.5 w-1.5 rounded-full bg-accent" />
                              )}
                            </button>
                          ))}
                        </div>
                      )}

                      {activeTab === 'export' && (
                        <div className="space-y-2">
                          <span className="mb-1 block text-xs font-semibold text-main">
                            {t('table.export')}
                          </span>
                          {(
                            [
                              { format: 'csv', label: t('table.exportCsv') },
                              { format: 'excel', label: t('table.exportExcel') },
                              { format: 'pdf', label: t('table.exportPdf') },
                            ] as const
                          ).map((item) => (
                            <button
                              key={item.format}
                              type="button"
                              disabled={!onExport}
                              onClick={() => onExport?.(item.format)}
                              className={cn(
                                'flex w-full items-center justify-between rounded-md border border-glass px-2.5 py-1.5 text-xs transition-colors',
                                onExport
                                  ? 'bg-surface text-main hover:border-accent/40 hover:bg-accent/5'
                                  : 'cursor-not-allowed bg-bg/30 text-muted opacity-70',
                              )}
                            >
                              <span>{item.label}</span>
                              {!onExport && <Badge tone="muted">{t('table.comingSoon')}</Badge>}
                            </button>
                          ))}
                        </div>
                      )}

                      {activeTab === 'refresh' && (
                        <div className="space-y-2">
                          <span className="mb-1 block text-xs font-semibold text-main">
                            {t('table.autoRefresh')}
                          </span>
                          {(
                            [
                              { interval: 0, label: t('table.refreshOff') },
                              { interval: 30, label: '30s' },
                              { interval: 60, label: '1m' },
                              { interval: 300, label: '5m' },
                            ] as const
                          ).map((item) => (
                            <button
                              key={item.interval}
                              type="button"
                              disabled={!onRefresh}
                              className={cn(
                                'flex w-full items-center justify-between rounded-md border border-glass px-2.5 py-1.5 text-xs transition-colors',
                                onRefresh
                                  ? 'bg-surface text-main hover:border-accent/40 hover:bg-accent/5'
                                  : 'cursor-not-allowed bg-bg/30 text-muted opacity-70',
                              )}
                            >
                              <span>{item.label}</span>
                              {!onRefresh && <Badge tone="muted">{t('table.comingSoon')}</Badge>}
                            </button>
                          ))}
                        </div>
                      )}
                    </div>
                  </div>
                )}
              </div>
            )}
          </div>
        </div>
      )}

      {/* Render Mode 1: Cards Grid */}
      {activeViewMode === 'card' ? (
        <div className="p-4">
          {loading ? (
            <div className="py-16 text-center">
              <Spinner className="border-muted/40 border-t-accent" />
            </div>
          ) : data.length === 0 ? (
            <EmptyState message={emptyMessage ?? t('table.noRecords')} icon={emptyIcon} />
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
              {data.map((row) => (
                <div
                  key={rowKey(row)}
                  className="rounded-lg border border-glass bg-surface p-4 shadow-sm hover:border-accent/30 transition-all flex flex-col justify-between"
                >
                  {cardRender ? (
                    cardRender(row)
                  ) : (
                    <div className="space-y-3">
                      <div className="flex items-start justify-between gap-2 border-b border-glass pb-2.5">
                        <div className="min-w-0 font-semibold text-main text-sm truncate">
                          {visibleColumns[0]?.render
                            ? visibleColumns[0].render(row)
                            : String((row as Record<string, unknown>)[visibleColumns[0]?.key] ?? '')}
                        </div>
                        {actions && <div className="shrink-0">{actions(row)}</div>}
                      </div>

                      <div className="space-y-1.5 text-xs">
                        {visibleColumns.slice(1).map((col) => (
                          <div key={col.key} className="flex items-center justify-between gap-2">
                            <span className="text-muted/70">{col.header}:</span>
                            <span className="text-main font-medium truncate">
                              {col.render
                                ? col.render(row)
                                : String((row as Record<string, unknown>)[col.key] ?? '')}
                            </span>
                          </div>
                        ))}
                      </div>
                    </div>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      ) : activeViewMode === 'list' ? (
        /* Render Mode 2: Compact List */
        <div className="p-2">
          {loading ? (
            <div className="py-16 text-center">
              <Spinner className="border-muted/40 border-t-accent" />
            </div>
          ) : data.length === 0 ? (
            <EmptyState message={emptyMessage ?? t('table.noRecords')} icon={emptyIcon} />
          ) : (
            <div className="divide-y divide-glass/60">
              {data.map((row) => (
                <div
                  key={rowKey(row)}
                  className="p-3 flex items-center justify-between gap-4 hover:bg-accent/[0.03] transition-colors rounded-lg"
                >
                  {listRender ? (
                    listRender(row)
                  ) : (
                    <>
                      <div className="flex flex-1 items-center gap-4 min-w-0 flex-wrap">
                        {visibleColumns.map((col, idx) => (
                          <div
                            key={col.key}
                            className={
                              idx === 0
                                ? 'min-w-[160px] font-semibold text-main text-sm'
                                : 'text-xs text-muted'
                            }
                          >
                            {col.render
                              ? col.render(row)
                              : String((row as Record<string, unknown>)[col.key] ?? '')}
                          </div>
                        ))}
                      </div>
                      {actions && <div className="shrink-0">{actions(row)}</div>}
                    </>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      ) : (
        /* Render Mode 3: Classic Table (Default) */
        <div className="overflow-x-auto">
          <table className="w-full border-collapse">
            <thead>
              <tr className="border-b border-glass bg-bg/40">
                {visibleColumns.map((col) => (
                  <th
                    key={col.key}
                    aria-sort={ariaSort(col)}
                    className={cn(
                      MICRO_LABEL,
                      'text-left',
                      thPadding,
                      col.className,
                    )}
                  >
                    {headerContent(col)}
                  </th>
                ))}
                {actions && (
                  <th
                    className={cn(
                      MICRO_LABEL,
                      'text-right',
                      thPadding,
                    )}
                  >
                    {actionsHeader}
                  </th>
                )}
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
                    <EmptyState message={emptyMessage ?? t('table.noRecords')} icon={emptyIcon} />
                  </td>
                </tr>
              ) : (
                data.map((row) => (
                  <tr
                    key={rowKey(row)}
                    className="border-b border-glass/60 transition-colors last:border-0 hover:bg-accent/[0.04]"
                  >
                    {visibleColumns.map((col) => (
                      <td key={col.key} className={cn('text-main', tdPadding, col.className)}>
                        {col.render
                          ? col.render(row)
                          : String((row as Record<string, unknown>)[col.key] ?? '')}
                      </td>
                    ))}
                    {actions && <td className={cn('text-right', tdPadding)}>{actions(row)}</td>}
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      )}

      <TablePagination
        page={page}
        pageSize={pageSize}
        totalElements={totalElements}
        totalPages={totalPages}
        onPageChange={onPageChange}
        pageSizeOptions={pageSizeOptions}
        onPageSizeChange={onPageSizeChange}
      />
    </div>
  );
}


