import { useEffect, useRef, useState } from 'react';
import { DetailPanel } from './DetailPanel';
import { Button } from '../ui/Button';
import { SelectInput } from '../ui/SelectInput';
import type { SelectOption } from '../../lib/select';
import { notify } from '../../lib/notify';
import { useT } from '../../lib/i18n';

interface AssignSectionProps<V extends string> {
  title: string;
  options: SelectOption<V>[];
  selectedValues: V[];
  /** Apply the working selection. Reject to keep editing (the global mutation
   *  onError surfaces the toast). */
  onSave: (values: V[]) => Promise<void>;
  saving?: boolean;
  placeholder?: string;
  emptySelectedHint?: string;
  /** Optional success toast shown after a successful save. */
  successMessage?: string;
}

/**
 * A detail-page section that edits a multi-selection (roles/groups/permissions/parents)
 * with a react-select multi and a Save button. Tracks a working draft that stays in sync
 * with the upstream {@link selectedValues} until the user edits it, so late-loading data
 * populates correctly without clobbering in-flight edits. Errors are surfaced by the
 * global mutation onError (toast); this component only owns the draft + save.
 */
export function AssignSection<V extends string>({
  title, options, selectedValues, onSave, saving, placeholder, emptySelectedHint, successMessage,
}: AssignSectionProps<V>) {
  const { t } = useT();
  const [draft, setDraft] = useState<V[]>(selectedValues);
  const interacted = useRef(false);

  // Sync draft with upstream until the user edits (covers late query load).
  useEffect(() => {
    if (!interacted.current) setDraft(selectedValues);
  }, [selectedValues]);

  const dirty =
    draft.length !== selectedValues.length || draft.some((v) => !selectedValues.includes(v));

  const save = async () => {
    try {
      await onSave(draft);
      if (successMessage) notify.success(successMessage);
    } catch {
      // Global mutation onError (notifyApiError) surfaces the toast.
    }
  };

  return (
    <DetailPanel title={title}>
      <SelectInput<V>
        isMulti
        isClearable
        options={options}
        value={options.filter((o) => draft.includes(o.value))}
        onChange={(next) => {
          interacted.current = true;
          setDraft(((next as SelectOption<V>[] | null) ?? []).map((o) => o.value));
        }}
        placeholder={placeholder ?? t('common.select')}
      />
      {draft.length === 0 && emptySelectedHint && (
        <p className="mt-2 text-xs text-muted/70">{emptySelectedHint}</p>
      )}
      {/* Standard action footer: bottom-right of the editing surface, default-size
          buttons (same rule as modal footers) — never in the panel header. */}
      <div className="mt-4 flex justify-end">
        <Button variant="primary" onClick={save} disabled={!dirty} loading={saving}>
          {t('common.save')}
        </Button>
      </div>
    </DetailPanel>
  );
}
