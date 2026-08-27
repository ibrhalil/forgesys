import { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { SelectInput } from '../../../components/ui/SelectInput';
import { useT } from '../../../lib/i18n';
import { customAppsApi } from '../api';
import { VIEW_RECORDS_PARAMS } from '../hooks';
import { firstTextProperty, recordTitle } from '../cellValue';
import type { CustomAppProperty } from '../types';

/**
 * RELATION property picker — searches the target customApp's records (first bounded
 * page, client-side contains filter over the target's title field = first TEXT
 * property). The fetch rides the shared records cache key, so resolvers and
 * pickers of the same target customApp share one request.
 */
export function RelationPicker({
  property,
  value,
  valueLabel,
  onChange,
  label,
  error,
  isClearable = true,
  size = 'md',
  placeholder,
}: {
  /** The RELATION property (its config carries the targetCustomAppId). */
  property: CustomAppProperty;
  value: string | null;
  /** Display label for the current value (e.g. resolved target title); falls back to the raw id. */
  valueLabel?: string;
  onChange: (value: string | null) => void;
  label?: string;
  error?: string | null;
  isClearable?: boolean;
  size?: 'md' | 'sm';
  placeholder?: string;
}) {
  const { t } = useT();
  const targetCustomAppId = property.config?.targetCustomAppId ?? null;
  const { data: detail } = useQuery({
    queryKey: ['customApps', targetCustomAppId],
    queryFn: () => customAppsApi.get(targetCustomAppId!),
    enabled: !!targetCustomAppId,
  });
  const { data: records } = useQuery({
    queryKey: ['customApps', targetCustomAppId, 'records', VIEW_RECORDS_PARAMS],
    queryFn: () => customAppsApi.listRecords(targetCustomAppId!, VIEW_RECORDS_PARAMS),
    enabled: !!targetCustomAppId,
  });

  const options = useMemo(() => {
    const titleProp = firstTextProperty(detail?.properties ?? []);
    return (records?.items ?? []).map((r) => ({ value: r.id, label: recordTitle(r, titleProp) }));
  }, [detail, records]);

  if (!targetCustomAppId) {
    return (
      <SelectInput<string>
        label={label}
        error={error}
        isDisabled
        placeholder={t('customApps.relationTargetMissing')}
        value={null}
      />
    );
  }

  return (
    <SelectInput<string>
      label={label}
      error={error}
      placeholder={placeholder ?? t('customApps.relationPickerPh')}
      isClearable={isClearable}
      size={size}
      isLoading={!records}
      options={options}
      noOptionsMessage={t('customApps.pickerNoOptions')}
      // A picked id is resolved from the loaded options themselves (edit mode
      // still prefers the caller's resolver label) — the control never flashes
      // a raw UUID after a selection.
      // Label precedence: the loaded option set first (a fresh pick must win over
      // a stale edit-mode seed), the caller's resolver label only while the id is
      // not among the options, the raw id as the last resort.
      value={value ? { value, label: options.find((o) => o.value === value)?.label ?? valueLabel ?? value } : null}
      onChange={(o) => onChange((o as { value: string } | null)?.value ?? null)}
      loadOptions={(input) => {
        const q = input.trim().toLowerCase();
        return Promise.resolve(q === '' ? options : options.filter((o) => o.label.toLowerCase().includes(q)));
      }}
    />
  );
}
