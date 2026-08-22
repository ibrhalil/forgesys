import { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { SelectInput } from '../../../components/ui/SelectInput';
import { useT } from '../../../lib/i18n';
import { appsApi } from '../api';
import { VIEW_RECORDS_PARAMS } from '../hooks';
import { firstTextProperty, recordTitle } from '../cellValue';
import type { AppProperty } from '../types';

/**
 * RELATION property picker — searches the target app's records (first bounded
 * page, client-side contains filter over the target's title field = first TEXT
 * property). The fetch rides the shared records cache key, so resolvers and
 * pickers of the same target app share one request.
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
  /** The RELATION property (its config carries the targetAppId). */
  property: AppProperty;
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
  const targetAppId = property.config?.targetAppId ?? null;
  const { data: detail } = useQuery({
    queryKey: ['apps', targetAppId],
    queryFn: () => appsApi.get(targetAppId!),
    enabled: !!targetAppId,
  });
  const { data: records } = useQuery({
    queryKey: ['apps', targetAppId, 'records', VIEW_RECORDS_PARAMS],
    queryFn: () => appsApi.listRecords(targetAppId!, VIEW_RECORDS_PARAMS),
    enabled: !!targetAppId,
  });

  const options = useMemo(() => {
    const titleProp = firstTextProperty(detail?.properties ?? []);
    return (records?.items ?? []).map((r) => ({ value: r.id, label: recordTitle(r, titleProp) }));
  }, [detail, records]);

  if (!targetAppId) {
    return (
      <SelectInput<string>
        label={label}
        error={error}
        isDisabled
        placeholder={t('apps.relationTargetMissing')}
        value={null}
      />
    );
  }

  return (
    <SelectInput<string>
      label={label}
      error={error}
      placeholder={placeholder ?? t('apps.relationPickerPh')}
      isClearable={isClearable}
      size={size}
      isLoading={!records}
      options={options}
      noOptionsMessage={t('apps.pickerNoOptions')}
      value={value ? { value, label: valueLabel ?? value } : null}
      onChange={(o) => onChange((o as { value: string } | null)?.value ?? null)}
      loadOptions={(input) => {
        const q = input.trim().toLowerCase();
        return Promise.resolve(q === '' ? options : options.filter((o) => o.label.toLowerCase().includes(q)));
      }}
    />
  );
}
