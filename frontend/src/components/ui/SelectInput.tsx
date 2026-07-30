import { useMemo } from 'react';
import type { ReactNode } from 'react';
import RSelect from 'react-select';
import AsyncSelect from 'react-select/async';
import CreatableSelect from 'react-select/creatable';
import AsyncCreatableSelect from 'react-select/async-creatable';
import { Field } from './Field';
import { cn } from '../../lib/cn';
import type { SelectOption } from '../../lib/select';
import { useT } from '../../lib/i18n';

export interface SelectInputProps<V> {
  id?: string;
  /** Label above the control. Omit when the select is already titled by its container. */
  label?: string;
  error?: string | null;
  hint?: ReactNode;
  placeholder?: string;
  /** Static option list (sync mode). Ignored when {@link loadOptions} is set. */
  options?: SelectOption<V>[];
  /** Provide to enable async loading (typeahead). */
  loadOptions?: (input: string) => Promise<SelectOption<V>[]>;
  /** Current value: a single option, an array (multi), or null/undefined. */
  value?: SelectOption<V> | SelectOption<V>[] | null;
  onChange?: (value: SelectOption<V> | SelectOption<V>[] | null) => void;
  isMulti?: boolean;
  /** Allow creating new options (Creatable, or AsyncCreatable with loadOptions). */
  creatable?: boolean;
  isClearable?: boolean;
  isDisabled?: boolean;
  isLoading?: boolean;
  /** Render the dropdown in a portal (default document.body) so it escapes overflow
   *  containers (e.g. the Modal scroll area). */
  menuPortalTarget?: HTMLElement | null;
  className?: string;
  noOptionsMessage?: string;
  loadingMessage?: string;
  formatCreateLabel?: (input: string) => string;
  /** md = form default; sm = compact (inline controls, e.g. a card's status mover). */
  size?: 'md' | 'sm';
}

/**
 * The single select component of the app, styled for the light corporate theme.
 * Behavior is fully prop-driven: single (default), multi (`isMulti`), searchable
 * async (`loadOptions`), creatable tags (`creatable`), clearable — and a compact
 * `size="sm"` for inline controls. Works in terms of {@link SelectOption} objects;
 * callers map to/from primitive ids where needed.
 */
export function SelectInput<V>({
  id,
  label,
  error,
  hint,
  placeholder,
  options,
  loadOptions,
  value,
  onChange,
  isMulti = false,
  creatable = false,
  isClearable = false,
  isDisabled = false,
  isLoading = false,
  menuPortalTarget,
  className,
  noOptionsMessage,
  loadingMessage,
  formatCreateLabel,
  size = 'md',
}: SelectInputProps<V>) {
  const { t } = useT();
  const inputId = useMemo(
    () => id ?? (label ? `sel-${label.toLowerCase().replace(/\s+/g, '-')}` : `sel-${Math.random().toString(36).slice(2)}`),
    [id, label],
  );
  const portal =
    menuPortalTarget !== undefined ? menuPortalTarget : typeof document !== 'undefined' ? document.body : null;

  // Pick the right react-select variant for the requested mode.
  const Component = loadOptions
    ? creatable
      ? AsyncCreatableSelect
      : AsyncSelect
    : creatable
      ? CreatableSelect
      : RSelect;

  // Tailwind theme applied through react-select's `classNames` (v5). Cast keeps the
  // generic component variants happy without per-variant type plumbing.
  const compact = size === 'sm';
  const classNames = {
    control: (state: { isDisabled?: boolean; isFocused?: boolean }) =>
      cn(
        compact ? 'min-h-[32px] cursor-pointer rounded-md text-[13px]' : 'min-h-[40px] cursor-text rounded-lg',
        'border !shadow-none',
        'bg-main/5',
        state.isDisabled && 'opacity-60',
        error ? 'border-danger/60' : state.isFocused ? 'border-accent/60' : 'border-glass',
      ),
    valueContainer: () => (compact ? 'px-2.5 py-1 gap-1' : 'px-3 py-1.5 gap-1.5'),
    input: () => 'text-main',
    placeholder: () => 'text-muted/60',
    singleValue: () => 'text-main',
    multiValue: () => 'flex items-center gap-1 rounded-md bg-accent/15 py-0.5 pl-2',
    multiValueLabel: () => 'text-xs font-medium text-accent',
    multiValueRemove: () => 'flex h-4 w-4 cursor-pointer items-center justify-center rounded text-accent/70 hover:bg-accent/25 hover:text-accent',
    indicatorsContainer: () => 'gap-1',
    indicatorSeparator: () => (compact ? 'my-1.5 bg-glass' : 'my-2 bg-glass'),
    dropdownIndicator: () => cn('text-muted hover:text-main', compact && 'p-1'),
    clearIndicator: () => cn('text-muted hover:text-main', compact && 'p-1'),
    menu: () => 'mt-1 overflow-hidden rounded-lg border border-glass bg-sidebar shadow-xl shadow-black/10',
    menuList: () => 'py-1',
    option: (state: { isSelected?: boolean; isFocused?: boolean }) =>
      cn(
        compact ? 'cursor-pointer px-3 py-1.5 text-[13px]' : 'cursor-pointer px-3 py-2 text-sm',
        state.isSelected ? 'bg-accent/15 font-medium text-accent' : state.isFocused ? 'bg-main/5 text-main' : 'text-main',
      ),
    noOptionsMessage: () => 'px-3 py-2 text-sm text-muted',
    loadingMessage: () => 'px-3 py-2 text-sm text-muted',
  };

  const control = (
    <Component
      inputId={inputId}
      placeholder={placeholder ?? t('common.select')}
      value={value as never}
      onChange={(next) => onChange?.(next as SelectOption<V> | SelectOption<V>[] | null)}
      options={options as never}
      loadOptions={loadOptions as never}
      isMulti={isMulti}
      isClearable={isClearable}
      isDisabled={isDisabled}
      isLoading={isLoading}
      menuPortalTarget={portal}
      styles={{ menuPortal: (base) => ({ ...base, zIndex: 60 }) }}
      classNames={classNames as never}
      noOptionsMessage={() => noOptionsMessage ?? t('common.noOptions')}
      loadingMessage={() => loadingMessage ?? t('common.loading')}
      formatCreateLabel={formatCreateLabel}
      theme={(t) => ({ ...t, colors: { ...t.colors, primary: '#c2185b', primary75: '#d81b60', primary50: '#c2185b2e', primary25: '#c2185b26', danger: '#dc2626', dangerLight: '#dc262622', neutral0: '#ffffff', neutral5: '#f8fafc', neutral10: '#f1f5f9', neutral20: '#e2e8f0', neutral30: '#cbd5e1', neutral40: '#94a3b8', neutral50: '#64748b', neutral60: '#475569', neutral80: '#1e293b' } })}
    />
  );

  // Without a label (and no error/hint), render bare — the container (e.g. DetailPanel) titles it.
  if (!label && !error && !hint) {
    return <div className={className}>{control}</div>;
  }

  return (
    <Field id={inputId} label={label ?? ''} error={error} hint={hint} className={className}>
      {control}
    </Field>
  );
}
