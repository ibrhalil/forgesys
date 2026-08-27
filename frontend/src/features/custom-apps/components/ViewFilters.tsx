import { useState } from 'react';
import { LuPlus, LuX } from 'react-icons/lu';
import { useT } from '../../../lib/i18n';
import { Button } from '../../../components/ui/Button';
import { SelectInput } from '../../../components/ui/SelectInput';
import type { SelectOption } from '../../../lib/select';
import { allowedOperators, operatorNeedsValue, toWireFilter } from '../viewQuery';
import type { CustomAppDetail, CustomAppValueFilter, CustomAppValueOperator, CustomAppValueSort } from '../types';
import { UserPicker } from '../../../components/pickers/UserPicker';
import { RelationPicker } from './RelationPicker';
import { INPUT_BASE_SM } from '../../../components/ui/styles';

/** Hard limits from the backend DTOs (filters ≤10, sorts ≤5). */
const MAX_FILTERS = 10;
const MAX_SORTS = 5;

const INPUT_CLASS = `min-w-24 ${INPUT_BASE_SM}`;

/**
 * Row-based editor for the structured view DSL: each filter row is
 * property + operator + a type-appropriate value control (no expression editor).
 * "Apply" feeds the transient client-side filter; "Save to view" persists the rows
 * into the view config via PUT (the parent keeps the type anchors intact).
 */
export function ViewFilters({
  customApp,
  seedFilters,
  seedSorts,
  canSave,
  saving,
  onApply,
  onSaveToView,
  onClear,
}: {
  customApp: CustomAppDetail;
  /** Effective rows the editor opens with (transient override ?? view config). */
  seedFilters: CustomAppValueFilter[];
  seedSorts: CustomAppValueSort[];
  canSave: boolean;
  saving?: boolean;
  onApply: (filters: CustomAppValueFilter[], sorts: CustomAppValueSort[]) => void;
  onSaveToView: (filters: CustomAppValueFilter[], sorts: CustomAppValueSort[]) => void;
  onClear: () => void;
}) {
  const { t } = useT();
  const [filters, setFilters] = useState<CustomAppValueFilter[]>(seedFilters);
  const [sorts, setSorts] = useState<CustomAppValueSort[]>(seedSorts);

  // FORMULA is rejected on create and never queryable — exclude it everywhere.
  const queryable = customApp.properties.filter((p) => p.type !== 'FORMULA');
  const propById = new Map(queryable.map((p) => [p.id, p]));
  const propOptions: SelectOption<string>[] = queryable.map((p) => ({ value: p.id, label: p.name }));
  const sortOptions: SelectOption<string>[] = [
    { value: 'createdAt', label: t('customApps.createdAt') },
    ...propOptions,
  ];
  const dirOptions: SelectOption<'asc' | 'desc'>[] = [
    { value: 'asc', label: t('customApps.sortAsc') },
    { value: 'desc', label: t('customApps.sortDesc') },
  ];

  const opOptions = (propertyId: string): SelectOption<CustomAppValueOperator>[] =>
    allowedOperators(propById.get(propertyId)?.type ?? 'TEXT').map((op) => ({
      value: op,
      label: t(`customApps.op.${op}`),
    }));

  const addFilter = () =>
    setFilters((rows) => {
      const prop = queryable[0];
      if (!prop) return rows;
      const op = allowedOperators(prop.type)[0] ?? 'IS_EMPTY';
      return [...rows, { propertyId: prop.id, operator: op }];
    });

  const setFilterProperty = (index: number, propertyId: string) =>
    setFilters((rows) =>
      rows.map((row, i) => {
        if (i !== index) return row;
        const allowed = allowedOperators(propById.get(propertyId)?.type ?? 'TEXT');
        const operator = (allowed.includes(row.operator) ? row.operator : allowed[0]) ?? 'IS_EMPTY';
        // The value type follows the property — reset on property switch.
        return { propertyId, operator };
      }),
    );

  const setFilterOperator = (index: number, operator: CustomAppValueOperator) =>
    setFilters((rows) => rows.map((row, i) => (i === index ? { ...row, operator } : row)));

  const setFilterValue = (index: number, value: string | number | undefined) =>
    setFilters((rows) => rows.map((row, i) => (i === index ? { ...row, value } : row)));

  const incomplete = filters.some(
    (f) => operatorNeedsValue(f.operator) && (f.value === undefined || f.value === null || f.value === ''),
  );
  const wireFilters = () => filters.map(toWireFilter);

  return (
    <div className="flex flex-col gap-4 rounded-lg border border-glass bg-bg/40 p-4">
      {/* ── Filter rows ── */}
      {filters.length > 0 && <p className="m-0 text-xs font-semibold uppercase tracking-wide text-muted">{t('customApps.filtersLabel')}</p>}
      <div className="flex flex-col gap-2">
        {filters.map((row, i) => {
          const prop = propById.get(row.propertyId);
          const needsValue = operatorNeedsValue(row.operator);
          const valueControl = () => {
            if (!needsValue || !prop) return <span className="text-xs text-muted/60">—</span>;
            const invalid = row.value === undefined || row.value === null || row.value === '';
            const cls = invalid ? `${INPUT_CLASS} border-danger/60` : INPUT_CLASS;
            switch (prop.type) {
              case 'NUMBER':
                return (
                  <input
                    aria-label={t('customApps.filterValue')}
                    type="number"
                    className={cls}
                    value={row.value === undefined || row.value === null ? '' : String(row.value)}
                    onChange={(e) => setFilterValue(i, e.target.value === '' ? undefined : Number(e.target.value))}
                  />
                );
              case 'DATE':
                return (
                  <input
                    aria-label={t('customApps.filterValue')}
                    type="date"
                    className={cls}
                    value={row.value === undefined || row.value === null ? '' : String(row.value)}
                    onChange={(e) => setFilterValue(i, e.target.value === '' ? undefined : e.target.value)}
                  />
                );
              case 'SELECT':
                return (
                  <SelectInput<string>
                    id={`filter-value-${i}`}
                    size="sm"
                    options={(prop.config?.options ?? []).map((o) => ({ value: o, label: o }))}
                    value={row.value !== undefined && row.value !== null ? { value: String(row.value), label: String(row.value) } : null}
                    onChange={(o) => setFilterValue(i, (o as SelectOption<string> | null)?.value ?? undefined)}
                    isClearable
                    className={invalid ? 'min-w-32 border-danger/60' : 'min-w-32'}
                  />
                );
              case 'USER':
                return (
                  <div className={invalid ? 'min-w-36 rounded-md border border-danger/60' : 'min-w-36'}>
                    <UserPicker
                      value={row.value != null ? String(row.value) : null}
                      onChange={(v) => setFilterValue(i, v ?? undefined)}
                      size="sm"
                      isClearable
                    />
                  </div>
                );
              case 'RELATION':
                return (
                  <div className={invalid ? 'min-w-36 rounded-md border border-danger/60' : 'min-w-36'}>
                    <RelationPicker
                      property={prop}
                      value={row.value != null ? String(row.value) : null}
                      onChange={(v) => setFilterValue(i, v ?? undefined)}
                      size="sm"
                      isClearable
                    />
                  </div>
                );
              default:
                return (
                  <input
                    aria-label={t('customApps.filterValue')}
                    type="text"
                    className={cls}
                    value={row.value === undefined || row.value === null ? '' : String(row.value)}
                    onChange={(e) => setFilterValue(i, e.target.value === '' ? undefined : e.target.value)}
                  />
                );
            }
          };
          return (
            <div key={i} className="flex flex-wrap items-center gap-2">
              <div className="min-w-36 flex-1">
                <SelectInput<string>
                  id={`filter-property-${i}`}
                  size="sm"
                  options={propOptions}
                  value={propOptions.find((o) => o.value === row.propertyId) ?? null}
                  onChange={(o) => setFilterProperty(i, (o as SelectOption<string> | null)?.value ?? row.propertyId)}
                />
              </div>
              <div className="min-w-32">
                <SelectInput<CustomAppValueOperator>
                  id={`filter-operator-${i}`}
                  size="sm"
                  options={opOptions(row.propertyId)}
                  value={opOptions(row.propertyId).find((o) => o.value === row.operator) ?? null}
                  onChange={(o) => setFilterOperator(i, (o as SelectOption<CustomAppValueOperator> | null)?.value ?? row.operator)}
                />
              </div>
              <div className="min-w-32 flex-1">{valueControl()}</div>
              <Button
                variant="ghost"
                size="sm"
                aria-label={t('customApps.filterRemove')}
                onClick={() => setFilters((rows) => rows.filter((_, j) => j !== i))}
              >
                <LuX aria-hidden className="h-4 w-4" />
              </Button>
            </div>
          );
        })}
      </div>

      {/* ── Sort rows ── */}
      {sorts.length > 0 && <p className="m-0 text-xs font-semibold uppercase tracking-wide text-muted">{t('customApps.sortsLabel')}</p>}
      <div className="flex flex-col gap-2">
        {sorts.map((row, i) => (
          <div key={i} className="flex flex-wrap items-center gap-2">
            <div className="min-w-36 flex-1">
              <SelectInput<string>
                id={`sort-property-${i}`}
                size="sm"
                options={sortOptions}
                value={sortOptions.find((o) => o.value === row.propertyId) ?? null}
                onChange={(o) =>
                  setSorts((rows) => rows.map((r, j) => (j === i ? { ...r, propertyId: (o as SelectOption<string>)?.value ?? r.propertyId } : r)))
                }
              />
            </div>
            <div className="min-w-32">
              <SelectInput<'asc' | 'desc'>
                id={`sort-direction-${i}`}
                size="sm"
                options={dirOptions}
                value={dirOptions.find((o) => o.value === row.direction) ?? null}
                onChange={(o) =>
                  setSorts((rows) => rows.map((r, j) => (j === i ? { ...r, direction: (o as SelectOption<'asc' | 'desc'>)?.value ?? r.direction } : r)))
                }
              />
            </div>
            <Button
              variant="ghost"
              size="sm"
              aria-label={t('customApps.sortRemove')}
              onClick={() => setSorts((rows) => rows.filter((_, j) => j !== i))}
            >
              <LuX aria-hidden className="h-4 w-4" />
            </Button>
          </div>
        ))}
      </div>

      {/* ── Row adders + actions ── */}
      <div className="flex flex-wrap items-center gap-2">
        {queryable.length > 0 && filters.length < MAX_FILTERS && (
          <Button variant="secondary" size="sm" onClick={addFilter}>
            <LuPlus aria-hidden className="h-4 w-4" />
            {t('customApps.filterAdd')}
          </Button>
        )}
        {queryable.length > 0 && sorts.length < MAX_SORTS && (
          <Button
            variant="secondary"
            size="sm"
            onClick={() => setSorts((rows) => [...rows, { propertyId: 'createdAt', direction: 'desc' }])}
          >
            <LuPlus aria-hidden className="h-4 w-4" />
            {t('customApps.sortAdd')}
          </Button>
        )}
        <span className="ml-auto text-xs text-muted/70">
          {incomplete ? t('customApps.filterIncomplete') : `${filters.length}/${MAX_FILTERS} · ${sorts.length}/${MAX_SORTS}`}
        </span>
      </div>
      <div className="mt-4 flex justify-end gap-3">
        <Button variant="ghost" onClick={onClear} disabled={filters.length === 0 && sorts.length === 0}>
          {t('customApps.filterClear')}
        </Button>
        {canSave && (
          <Button
            variant="secondary"
            disabled={incomplete}
            loading={saving}
            onClick={() => onSaveToView(wireFilters(), sorts)}
          >
            {t('customApps.filterSaveToView')}
          </Button>
        )}
        <Button
          variant="primary"
          disabled={incomplete}
          onClick={() => onApply(wireFilters(), sorts)}
        >
          {t('customApps.filterApply')}
        </Button>
      </div>
    </div>
  );
}
