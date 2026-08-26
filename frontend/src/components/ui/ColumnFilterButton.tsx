import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { LuListFilter, LuX } from 'react-icons/lu';
import { cn } from '../../lib/cn';
import { useT } from '../../lib/i18n';
import type { SelectOption } from '../../lib/select';
import type { FilterCriteria, FilterOperator } from '../../types';
import { Button } from './Button';
import { SelectInput } from './SelectInput';

/** Value-control kinds; drives the input rendering and the default operator set. */
export type ColumnFilterControl = 'text' | 'number' | 'date' | 'boolean' | 'select' | 'multiselect';

export interface ColumnFilterSpec {
  /** Backend filter-engine field name (must be in the feature's FILTER_FIELDS whitelist). */
  field: string;
  control: ColumnFilterControl;
  /** Operator whitelist; defaults per `control` when omitted. */
  operators?: FilterOperator[];
  /** Fixed options for `select`/`multiselect` controls. */
  options?: SelectOption<string>[];
  /**
   * Async option loader for `select`/`multiselect` (e.g. membership pickers) —
   * ignored when `options` is provided.
   */
  optionsLoader?: (input: string) => Promise<SelectOption<string>[]>;
}

const TEXT_OPS: FilterOperator[] = ['CONTAINS', 'EQ', 'NOT_EQ', 'STARTS_WITH', 'ENDS_WITH', 'IS_NULL', 'IS_NOT_NULL'];
const NUMBER_OPS: FilterOperator[] = ['EQ', 'NOT_EQ', 'GT', 'GTE', 'LT', 'LTE', 'IS_NULL', 'IS_NOT_NULL'];
const BOOLEAN_OPS: FilterOperator[] = ['EQ', 'IS_NULL', 'IS_NOT_NULL'];
const SELECT_OPS: FilterOperator[] = ['EQ', 'NOT_EQ', 'IS_NULL', 'IS_NOT_NULL'];
const MULTI_OPS: FilterOperator[] = ['IN', 'NOT_IN', 'IS_NULL', 'IS_NOT_NULL'];

/** Operators that carry no value (IS_NULL / IS_NOT_NULL). */
export const operatorNeedsNoValue = (op: FilterOperator): boolean => op === 'IS_NULL' || op === 'IS_NOT_NULL';

export function defaultOperators(control: ColumnFilterControl): FilterOperator[] {
  switch (control) {
    case 'text':
      return TEXT_OPS;
    case 'number':
    case 'date':
      return NUMBER_OPS;
    case 'boolean':
      return BOOLEAN_OPS;
    case 'select':
      return SELECT_OPS;
    case 'multiselect':
      return MULTI_OPS;
  }
}

const INPUT_CLASS =
  'w-full rounded-md border border-glass bg-main/5 px-2 py-1.5 text-sm text-main ' +
  'placeholder:text-muted/50 focus:outline-none focus:ring-2 focus:ring-accent/50';

const PANEL_WIDTH = 256; // w-64
/** Flip decision fallback when layout measurement is unavailable (e.g. jsdom). */
const PANEL_FALLBACK_HEIGHT = 240;

interface ColumnFilterButtonProps {
  spec: ColumnFilterSpec;
  header: string;
  /** The active criteria for this field (controlled), or none. */
  active?: FilterCriteria;
  onChange: (criteria: FilterCriteria | null) => void;
}

/**
 * Per-column structured-filter trigger for DataTable headers (K-49): operator
 * select + type-appropriate value control producing a backend {@link FilterCriteria}.
 * State is controlled by the page (`useListPageState.filters`). The popover renders
 * as a fixed-position body portal (z-60 of the menu scale) so overflow cannot clip
 * it, and flips above the trigger near the viewport bottom.
 */
export function ColumnFilterButton({ spec, header, active, onChange }: ColumnFilterButtonProps) {
  const { t } = useT();
  const [open, setOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const panelRef = useRef<HTMLDivElement>(null);
  const [pos, setPos] = useState<{ top: number; left: number } | null>(null);

  useLayoutEffect(() => {
    if (!open) return;
    const rect = triggerRef.current?.getBoundingClientRect();
    if (!rect) return;
    const height = panelRef.current?.offsetHeight || PANEL_FALLBACK_HEIGHT;
    const openUp = rect.bottom + height + 12 > window.innerHeight;
    const top = openUp ? Math.max(8, rect.top - height - 6) : rect.bottom + 6;
    const left = Math.max(8, Math.min(rect.left, window.innerWidth - PANEL_WIDTH - 8));
    setPos({ top, left });
  }, [open]);

  const operators = spec.operators ?? defaultOperators(spec.control);
  const [operator, setOperator] = useState<FilterOperator>(active?.operator ?? operators[0]);
  const [value, setValue] = useState<string>(active?.values[0] ?? '');
  const [secondValue, setSecondValue] = useState<string>(active?.values[1] ?? '');
  const [multiValue, setMultiValue] = useState<SelectOption<string>[]>(
    spec.options?.filter((o) => (active?.values ?? []).includes(o.value)) ?? [],
  );

  // Re-sync the draft when the active clause changes from outside (e.g. page reset).
  useEffect(() => {
    if (!open) {
      setOperator(active?.operator ?? operators[0]);
      setValue(active?.values[0] ?? '');
      setSecondValue(active?.values[1] ?? '');
      setMultiValue(spec.options?.filter((o) => (active?.values ?? []).includes(o.value)) ?? []);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [active, open]);

  useEffect(() => {
    if (!open) return;
    const insideFilterSurface = (target: EventTarget | null): boolean => {
      const el = target as HTMLElement | null;
      return (
        !!el &&
        (!!containerRef.current?.contains(el) ||
          !!panelRef.current?.contains(el) ||
          !!el.closest?.('[role="listbox"]'))
      );
    };
    const onPointerDown = (e: MouseEvent | TouchEvent) => {
      if (!insideFilterSurface(e.target)) setOpen(false);
    };
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        setOpen(false);
        triggerRef.current?.focus();
      }
    };
    // Any scroll outside the popover (incl. the table's overflow-x container) closes
    // it — simpler than repositioning a fixed portal (same trade-off as RowMenu).
    const onScroll = (e: Event) => {
      if (!(e.target instanceof HTMLElement) || !insideFilterSurface(e.target)) setOpen(false);
    };
    const onResize = () => setOpen(false);
    document.addEventListener('mousedown', onPointerDown);
    document.addEventListener('touchstart', onPointerDown);
    document.addEventListener('keydown', onKeyDown);
    window.addEventListener('scroll', onScroll, true);
    window.addEventListener('resize', onResize);
    return () => {
      document.removeEventListener('mousedown', onPointerDown);
      document.removeEventListener('touchstart', onPointerDown);
      document.removeEventListener('keydown', onKeyDown);
      window.removeEventListener('scroll', onScroll, true);
      window.removeEventListener('resize', onResize);
    };
  }, [open]);

  const opOptions: SelectOption<FilterOperator>[] = operators.map((op) => ({
    value: op,
    label: t(`filter.op.${op}`),
  }));

  const incomplete =
    !operatorNeedsNoValue(operator) &&
    (operator === 'BETWEEN'
      ? value === '' || secondValue === ''
      : spec.control === 'multiselect'
        ? multiValue.length === 0
        : value === '');

  const apply = () => {
    if (operatorNeedsNoValue(operator)) {
      onChange({ field: spec.field, operator, values: [] });
    } else if (operator === 'BETWEEN') {
      onChange({ field: spec.field, operator, values: [value, secondValue] });
    } else if (spec.control === 'multiselect') {
      onChange({ field: spec.field, operator, values: multiValue.map((o) => o.value) });
    } else {
      onChange({ field: spec.field, operator, values: [value] });
    }
    setOpen(false);
  };

  const clear = () => {
    onChange(null);
    setValue('');
    setSecondValue('');
    setMultiValue([]);
    setOpen(false);
  };

  const booleanOptions: SelectOption<string>[] = [
    { value: 'true', label: t('filter.true') },
    { value: 'false', label: t('filter.false') },
  ];

  const valueControl = () => {
    if (operatorNeedsNoValue(operator)) return null;
    if (spec.control === 'multiselect') {
      return (
        <SelectInput<string>
          id={`filter-multi-${spec.field}`}
          size="sm"
          loadOptions={spec.options ? undefined : spec.optionsLoader}
          defaultOptions={!!spec.optionsLoader}
          options={spec.options}
          value={multiValue}
          onChange={(v) => setMultiValue(((v as SelectOption<string>[]) ?? []).filter((o) => o != null))}
          isMulti
          isClearable
        />
      );
    }
    if (spec.control === 'select' || spec.control === 'boolean') {
      const options = spec.control === 'boolean'
        ? booleanOptions
        : spec.options ?? [];
      if (!options.length && spec.optionsLoader) {
        return (
          <SelectInput<string>
            id={`filter-value-${spec.field}`}
            size="sm"
            loadOptions={spec.optionsLoader}
            defaultOptions
            value={value ? { value, label: value } : null}
            onChange={(o) => setValue((o as SelectOption<string> | null)?.value ?? '')}
            isClearable
          />
        );
      }
      return (
        <SelectInput<string>
          id={`filter-value-${spec.field}`}
          size="sm"
          options={options}
          value={options.find((o) => o.value === value) ?? null}
          onChange={(o) => setValue((o as SelectOption<string> | null)?.value ?? '')}
          isClearable
        />
      );
    }
    const type = spec.control === 'number' ? 'number' : spec.control === 'date' ? 'date' : 'text';
    return (
      <input
        aria-label={t('filter.value')}
        type={type}
        className={cn(INPUT_CLASS, incomplete && value === '' && 'border-danger/60')}
        value={value}
        onChange={(e) => setValue(e.target.value)}
      />
    );
  };

  return (
    <div ref={containerRef} className="relative inline-flex">
      <button
        ref={triggerRef}
        type="button"
        onClick={(e) => {
          e.stopPropagation();
          setOpen((v) => !v);
        }}
        title={t('filter.title', { field: header })}
        aria-label={t('filter.title', { field: header })}
        aria-expanded={open}
        aria-haspopup="dialog"
        className={cn(
          'inline-flex h-4 w-4 items-center justify-center rounded text-muted/60 transition-colors hover:text-main focus:outline-none focus-visible:ring-1 focus-visible:ring-accent',
          active && 'text-accent',
          open && 'text-main',
        )}
      >
        <LuListFilter className="h-3 w-3" aria-hidden />
      </button>

      {open &&
        createPortal(
          <div
            ref={panelRef}
            role="dialog"
            aria-label={t('filter.title', { field: header })}
            style={{
              position: 'fixed',
              top: pos?.top,
              left: pos?.left,
              width: PANEL_WIDTH,
              zIndex: 60,
            }}
            className="rounded-xl border border-glass bg-surface p-3 text-left shadow-xl shadow-black/15"
          >
            <div className="mb-2 flex items-center justify-between gap-2">
              <span className="truncate text-xs font-semibold text-main">{header}</span>
              {active && (
                <button
                  type="button"
                  onClick={clear}
                  className="inline-flex items-center gap-1 text-[11px] font-medium text-accent hover:underline"
                >
                  <LuX className="h-3 w-3" aria-hidden />
                  {t('filter.clear')}
                </button>
              )}
            </div>

            <div className="space-y-2">
              <SelectInput<FilterOperator>
                id={`filter-op-${spec.field}`}
                size="sm"
                options={opOptions}
                value={opOptions.find((o) => o.value === operator) ?? null}
                onChange={(o) => setOperator((o as SelectOption<FilterOperator> | null)?.value ?? operator)}
              />
              {valueControl()}
              {operator === 'BETWEEN' && !operatorNeedsNoValue(operator) && (
                <input
                  aria-label={t('filter.valueTo')}
                  type={spec.control === 'number' ? 'number' : spec.control === 'date' ? 'date' : 'text'}
                  className={cn(INPUT_CLASS, incomplete && secondValue === '' && 'border-danger/60')}
                  value={secondValue}
                  onChange={(e) => setSecondValue(e.target.value)}
                />
              )}
              <div className="flex justify-end gap-2 pt-1">
                <Button variant="ghost" size="sm" onClick={() => setOpen(false)}>
                  {t('common.cancel')}
                </Button>
                <Button variant="secondary" size="sm" disabled={incomplete} onClick={apply}>
                  {t('filter.apply')}
                </Button>
              </div>
            </div>
          </div>,
          document.body,
        )}
    </div>
  );
}
