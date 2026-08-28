import { useEffect, useMemo, useState } from 'react';
import { LuRefreshCw, LuX } from 'react-icons/lu';
import { useT } from '../../lib/i18n';
import type { FilterCriteria } from '../../types';
import { operatorNeedsNoValue, type ColumnFilterSpec } from './ColumnFilterButton';

export interface FilterChipColumn {
  header: string;
  filter?: ColumnFilterSpec;
}

interface FilterChipsProps {
  /** The table's columns — resolves a clause's field to its header label + filter spec. */
  columns: FilterChipColumn[];
  filters: FilterCriteria[];
  onFiltersChange: (filters: FilterCriteria[]) => void;
}

/**
 * Active-filter chip row for DataTable (K-55): one chip per live clause
 * (`header · operator · value`), removable individually, plus a clear-all link.
 * Values resolve through the column's static options (value→label) and, for async
 * specs, a one-shot `optionsLoader('')` id→label cache with raw-value fallback.
 */
export function FilterChips({ columns, filters, onFiltersChange }: FilterChipsProps) {
  const { t } = useT();

  const specByField = useMemo(() => {
    const map = new Map<string, { header: string; spec?: ColumnFilterSpec }>();
    columns.forEach((c) => {
      if (c.filter) map.set(c.filter.field, { header: c.header, spec: c.filter });
    });
    return map;
  }, [columns]);

  // Async id→label resolution: one loader call per async field while mounted; the
  // signature key keeps the effect stable across the caller's inline spec identities.
  const asyncSignature = useMemo(
    () =>
      filters
        .map((f) => specByField.get(f.field)?.spec)
        .filter((s): s is ColumnFilterSpec => !!s?.optionsLoader && !s.options)
        .map((s) => s.field)
        .sort()
        .join('|'),
    [filters, specByField],
  );
  const [asyncLabels, setAsyncLabels] = useState<Record<string, string>>({});
  useEffect(() => {
    if (!asyncSignature) return;
    let cancelled = false;
    const specs = asyncSignature
      .split('|')
      .map((field) => specByField.get(field)?.spec)
      .filter((s): s is ColumnFilterSpec => !!s?.optionsLoader);
    Promise.all(specs.map((s) => s.optionsLoader!('').catch(() => [])))
      .then((results) => {
        if (cancelled) return;
        setAsyncLabels((prev) => {
          const next = { ...prev };
          results.flat().forEach((o) => {
            next[o.value] = o.label;
          });
          return next;
        });
      });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [asyncSignature]);

  if (!filters.length) return null;

  const labelFor = (spec: ColumnFilterSpec | undefined, value: string): string => {
    if (spec?.control === 'boolean') {
      return value === 'true' ? t('filter.true') : t('filter.false');
    }
    const staticLabel = spec?.options?.find((o) => o.value === value)?.label;
    return staticLabel ?? asyncLabels[value] ?? value;
  };

  const remove = (field: string) => onFiltersChange(filters.filter((f) => f.field !== field));

  return (
    <span role="group" aria-label={t('filter.active')} className="inline-flex flex-wrap items-center gap-1.5">
      {filters.map((f) => {
        const entry = specByField.get(f.field);
        const header = entry?.header ?? f.field;
        const spec = entry?.spec;
        const valueText = operatorNeedsNoValue(f.operator)
          ? null
          : f.operator === 'BETWEEN'
            ? `${labelFor(spec, f.values[0] ?? '')} → ${labelFor(spec, f.values[1] ?? '')}`
            : f.values.map((v) => labelFor(spec, v)).join(', ');
        return (
          <span
            key={f.field}
            className="inline-flex max-w-full items-center gap-1.5 rounded bg-accent/15 py-0.5 pl-2 pr-1 text-xs text-accent"
          >
            <span className="truncate">
              <span className="font-medium">{header}</span>
              <span className="text-accent/70"> {t(`filter.op.${f.operator}`)}</span>
              {valueText != null && <span className="font-medium"> {valueText}</span>}
            </span>
            <button
              type="button"
              onClick={() => remove(f.field)}
              aria-label={t('filter.remove')}
              className="rounded p-0.5 text-accent/60 transition-colors hover:bg-accent/10 hover:text-accent focus:outline-none focus-visible:ring-2 focus-visible:ring-accent/60"
            >
              <LuX className="h-3 w-3" aria-hidden />
            </button>
          </span>
        );
      })}
      <button
        type="button"
        onClick={() => onFiltersChange([])}
        className="ml-1 text-[11px] font-medium text-accent hover:underline focus:outline-none focus-visible:ring-2 focus-visible:ring-accent/60"
      >
        {t('filter.clearAll')}
      </button>
    </span>
  );
}

/** Countdown display: `Ns` below a minute, `M:SS` above (machine meta, mono digits). */
export function refreshCountdownLabel(ms: number): string {
  const totalSeconds = Math.max(0, Math.ceil(ms / 1000));
  if (totalSeconds < 60) return `${totalSeconds}s`;
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}:${String(seconds).padStart(2, '0')}`;
}

interface RefreshStatusChipProps {
  /** The configured interval (fallback label while the countdown initializes). */
  intervalMs: number;
  /** Milliseconds until the next automatic refresh. */
  remainingMs: number;
  /** Click = refresh now + countdown refilled to the full interval. */
  onRefreshNow: () => void;
}

/**
 * Auto-refresh status chip for the chips row (K-55): a live countdown to the next
 * automatic refresh; clicking refreshes immediately and refills the countdown.
 * Muted neutral tone (a status, not a filter); interval changes live in the table
 * settings menu.
 */
export function RefreshStatusChip({ intervalMs, remainingMs, onRefreshNow }: RefreshStatusChipProps) {
  const { t } = useT();
  return (
    <button
      type="button"
      onClick={onRefreshNow}
      title={t('table.refreshNow')}
      aria-label={t('table.refreshNow')}
      className="ml-auto inline-flex items-center gap-1.5 rounded bg-main/5 px-2 py-0.5 text-xs text-muted transition-colors hover:text-main focus:outline-none focus-visible:ring-2 focus-visible:ring-accent/60"
    >
      <LuRefreshCw className="h-3 w-3" aria-hidden />
      <span className="font-mono tabular-nums">
        {t('table.autoRefreshActive', { interval: refreshCountdownLabel(remainingMs || intervalMs) })}
      </span>
    </button>
  );
}
