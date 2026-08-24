import { useMemo, useState } from 'react';
import { SelectInput } from '../ui/SelectInput';
import { useT } from '../../lib/i18n';
import type { SelectOption } from '../../lib/select';
import { useDebouncedLoadOptions } from './useDebouncedLoadOptions';

/** Shared prop surface of the five reference-data pickers (single- and multi-mode). */
export interface ReferencePickerProps {
  // single mode
  value?: string | null;
  /** Display label for the current value (e.g. a resolved detail); falls back to the
   *  last search/pick result, then the raw id. */
  valueLabel?: string;
  onChange?: (value: string | null) => void;
  // multi mode (assignment surfaces)
  isMulti?: boolean;
  values?: string[];
  /** id→label seed for the current selection (upstream summaries). */
  selectedOptions?: SelectOption<string>[];
  onValuesChange?: (ids: string[]) => void;
  // common
  label?: string;
  error?: string | null;
  isClearable?: boolean;
  size?: 'md' | 'sm';
  placeholder?: string;
  /** Debounce window for the typeahead; 0 disables (tests). */
  debounceMs?: number;
  /** Filtered out of the results (e.g. AppPicker's self-app exclusion). */
  excludeIds?: string[];
  className?: string;
}

/**
 * Internal core of the reference pickers: debounced async `loadOptions` over
 * SelectInput with id→label bookkeeping. Search results merge into a
 * monotonically-growing label map (seeded from `selectedOptions`), so single
 * mode never flashes a raw id after a pick and multi mode keeps every selected
 * chip labeled — ids outside any seen option still render as the raw id.
 */
export function ReferencePicker({
  search,
  ...props
}: ReferencePickerProps & {
  /** Raw (undebounced) option fetcher the picker wraps. */
  search: (input: string) => Promise<SelectOption<string>[]>;
}) {
  const { t } = useT();
  const {
    value,
    valueLabel,
    onChange,
    isMulti,
    values,
    selectedOptions,
    onValuesChange,
    label,
    error,
    isClearable = true,
    size = 'md',
    placeholder,
    debounceMs = 300,
    excludeIds,
    className,
  } = props;

  const [labels, setLabels] = useState<Map<string, string>>(new Map());

  const loadOptions = useDebouncedLoadOptions(async (input) => {
    const options = await search(input);
    if (options.length > 0) {
      setLabels((prev) => {
        const m = new Map(prev);
        let grew = false;
        for (const o of options) {
          if (!m.has(o.value)) {
            m.set(o.value, o.label);
            grew = true;
          }
        }
        return grew ? m : prev;
      });
    }
    const excluded = new Set(excludeIds ?? []);
    return excluded.size === 0 ? options : options.filter((o) => !excluded.has(o.value));
  }, debounceMs);

  const labelMap = useMemo(() => {
    const m = new Map(labels);
    for (const o of selectedOptions ?? []) m.set(o.value, o.label);
    return m;
  }, [labels, selectedOptions]);

  const singleValue = value ? { value, label: valueLabel ?? labelMap.get(value) ?? value } : null;
  const multiValue = (values ?? []).map((id) => ({ value: id, label: labelMap.get(id) ?? id }));

  return (
    <SelectInput<string>
      className={className}
      label={label}
      error={error}
      placeholder={placeholder ?? t('common.typeToSearch')}
      isClearable={isClearable}
      isMulti={isMulti}
      size={size}
      loadOptions={loadOptions}
      // Menu-open shows the first page without typing (react-select only fetches
      // on non-empty input changes otherwise).
      defaultOptions
      noOptionsMessage={t('apps.pickerNoOptions')}
      value={isMulti ? multiValue : singleValue}
      onChange={(next) => {
        if (isMulti) {
          const opts = ((next ?? []) as SelectOption<string>[]).slice();
          if (opts.length > 0) {
            setLabels((prev) => {
              const m = new Map(prev);
              for (const o of opts) m.set(o.value, o.label);
              return m;
            });
          }
          onValuesChange?.(opts.map((o) => o.value));
          return;
        }
        onChange?.((next as SelectOption<string> | null)?.value ?? null);
      }}
    />
  );
}
