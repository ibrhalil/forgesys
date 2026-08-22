import { usersApi } from '../../users/api';
import { SelectInput } from '../../../components/ui/SelectInput';
import { useT } from '../../../lib/i18n';

/**
 * USER property picker — async typeahead over the user directory (SelectInput's
 * first `loadOptions` consumer). Labels are emails (TaskBoard precedent); the
 * backend visibility scope applies to whatever the caller may see.
 */
export function UserPicker({
  value,
  valueLabel,
  onChange,
  label,
  error,
  isClearable = true,
  size = 'md',
  placeholder,
}: {
  value: string | null;
  /** Display label for the current value (e.g. resolved email); falls back to the raw id. */
  valueLabel?: string;
  onChange: (value: string | null) => void;
  label?: string;
  error?: string | null;
  isClearable?: boolean;
  size?: 'md' | 'sm';
  placeholder?: string;
}) {
  const { t } = useT();
  return (
    <SelectInput<string>
      label={label}
      error={error}
      placeholder={placeholder ?? t('apps.userPickerPh')}
      isClearable={isClearable}
      size={size}
      loadOptions={(input) =>
        usersApi
          .list({ q: input, size: 20, sorts: [{ field: 'email', dir: 'asc' }] })
          .then((page) => page.items.map((u) => ({ value: u.id, label: u.email })))
          .catch(() => [])
      }
      noOptionsMessage={t('apps.pickerNoOptions')}
      value={value ? { value, label: valueLabel ?? value } : null}
      onChange={(o) => onChange((o as { value: string } | null)?.value ?? null)}
    />
  );
}
