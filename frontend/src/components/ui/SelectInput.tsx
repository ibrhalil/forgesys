import { useMemo } from 'react';
import type { ComponentProps, ReactNode } from 'react';
import { components as RSComponents } from 'react-select';
import RSelect from 'react-select';
import AsyncSelect from 'react-select/async';
import CreatableSelect from 'react-select/creatable';
import AsyncCreatableSelect from 'react-select/async-creatable';
import { LuChevronDown, LuX } from 'react-icons/lu';
import { Field } from './Field';
import { cn } from '../../lib/cn';
import { POPOVER_MENU } from './styles';
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
  /** Async mode: load the first page on mount (menu-open shows options before typing). */
  defaultOptions?: boolean;
  /** Current value: a single option, an array (multi), or null/undefined. */
  value?: SelectOption<V> | SelectOption<V>[] | null;
  onChange?: (value: SelectOption<V> | SelectOption<V>[] | null) => void;
  isMulti?: boolean;
  /** Allow creating new options (Creatable, or AsyncCreatable with loadOptions). */
  creatable?: boolean;
  isClearable?: boolean;
  isDisabled?: boolean;
  /** Marks individual options as non-selectable (rendered muted, e.g. unsupported types). */
  isOptionDisabled?: (option: SelectOption<V>) => boolean;
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

// Lucide indicators (K-54: Lucide-only iconography) — module level so react-select
// never remounts the indicator subtrees between renders.
const selectComponents = {
  DropdownIndicator: (props: ComponentProps<typeof RSComponents.DropdownIndicator>) => (
    <RSComponents.DropdownIndicator {...props}>
      <LuChevronDown aria-hidden className="h-4 w-4" />
    </RSComponents.DropdownIndicator>
  ),
  ClearIndicator: (props: ComponentProps<typeof RSComponents.ClearIndicator>) => (
    <RSComponents.ClearIndicator {...props}>
      <LuX aria-hidden className="h-3.5 w-3.5" />
    </RSComponents.ClearIndicator>
  ),
};

/**
 * The single select component of the app (K-54 contract). Rendered with react-select
 * `unstyled` — visual emotion styles are dropped, the Tailwind `classNames` below
 * own the whole look; the tiny `styles` override only sets control height and the
 * portal z-index. Behavior is fully prop-driven: single (default), multi (`isMulti`),
 * searchable async (`loadOptions`), creatable tags (`creatable`), clearable — and a
 * compact `size="sm"` for inline controls. Works in terms of {@link SelectOption}
 * objects; callers map to/from primitive ids where needed.
 */
export function SelectInput<V>({
  id,
  label,
  error,
  hint,
  placeholder,
  options,
  loadOptions,
  defaultOptions,
  value,
  onChange,
  isMulti = false,
  creatable = false,
  isClearable = false,
  isDisabled = false,
  isOptionDisabled,
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

  const compact = size === 'sm';
  const classNames = {
    control: (state: { isDisabled?: boolean; isFocused?: boolean }) =>
      cn(
        compact ? 'cursor-pointer rounded text-[13px]' : 'cursor-text rounded-md',
        'border bg-main/5 transition-colors',
        state.isDisabled && 'opacity-50 cursor-not-allowed',
        state.isFocused && 'ring-2 ring-accent/50',
        error ? 'border-danger/60' : 'border-glass',
      ),
    valueContainer: () => (compact ? 'px-2.5 py-1 gap-1' : 'px-3 py-1.5 gap-1.5'),
    input: () => 'text-main',
    placeholder: () => 'text-muted/50',
    singleValue: () => 'text-main',
    multiValue: () => 'flex items-center gap-1 rounded bg-accent/15 py-0.5 pl-2',
    multiValueLabel: () => 'text-xs font-medium text-accent',
    multiValueRemove: () =>
      'flex h-4 w-4 cursor-pointer items-center justify-center rounded text-accent/70 hover:bg-accent/25 hover:text-accent',
    indicatorsContainer: () => 'gap-1',
    indicatorSeparator: () => (compact ? 'my-1.5 w-px bg-glass' : 'my-2 w-px bg-glass'),
    dropdownIndicator: () => cn('text-muted hover:text-main', compact ? 'p-1' : 'p-2'),
    clearIndicator: () => cn('text-muted hover:text-main', compact ? 'p-1' : 'p-2'),
    menu: () => cn('mt-1', POPOVER_MENU),
    menuList: () => 'py-1',
    option: (state: { isSelected?: boolean; isFocused?: boolean; isDisabled?: boolean }) =>
      cn(
        compact ? 'cursor-pointer px-2.5 py-1.5 text-xs' : 'cursor-pointer px-3 py-2 text-sm',
        state.isSelected
          ? 'bg-accent/15 font-medium text-accent'
          : state.isFocused
            ? 'bg-main/5 text-main'
            : 'text-main',
        state.isDisabled && 'cursor-not-allowed text-muted/50',
      ),
    noOptionsMessage: () => 'px-3 py-2 text-sm text-muted',
    loadingMessage: () => 'px-3 py-2 text-sm text-muted',
  };

  const control = (
    <Component
      inputId={inputId}
      unstyled
      placeholder={placeholder ?? t('common.select')}
      value={value as never}
      onChange={(next) => onChange?.(next as SelectOption<V> | SelectOption<V>[] | null)}
      options={options as never}
      loadOptions={loadOptions as never}
      isMulti={isMulti}
      isClearable={isClearable}
      isDisabled={isDisabled}
      isOptionDisabled={isOptionDisabled as never}
      isLoading={isLoading}
      defaultOptions={loadOptions ? defaultOptions : undefined}
      menuPortalTarget={portal}
      components={selectComponents as never}
      styles={{
        menuPortal: (base) => ({ ...base, zIndex: 60 }),
        control: (base) => ({ ...base, minHeight: compact ? 32 : 38 }),
      }}
      classNames={classNames as never}
      noOptionsMessage={() => noOptionsMessage ?? t('common.noOptions')}
      loadingMessage={() => loadingMessage ?? t('common.loading')}
      formatCreateLabel={formatCreateLabel}
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
