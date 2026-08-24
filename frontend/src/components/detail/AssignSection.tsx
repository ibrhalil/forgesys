import { useEffect, useRef, useState } from 'react';
import { DetailPanel } from './DetailPanel';
import { Button } from '../ui/Button';
import { SelectInput } from '../ui/SelectInput';
import type { SelectOption } from '../../lib/select';
import { notify } from '../../lib/notify';
import { useT } from '../../lib/i18n';
import { useDebouncedLoadOptions } from '../pickers/useDebouncedLoadOptions';

interface AssignSectionProps<V extends string> {
  title: string;
  /** Static option list (sync mode) — ignored when {@link loadOptions} is given. */
  options?: SelectOption<V>[];
  /** Async typeahead fetcher (async mode): switches the select to isMulti-async. */
  loadOptions?: (input: string) => Promise<SelectOption<V>[]>;
  selectedValues: V[];
  /** id→label seed for the current selection (async mode — upstream summaries). */
  selectedOptions?: SelectOption<V>[];
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
 * with a react-select multi and a Save button. Two data modes: static `options`
 * (small bounded lists) or async `loadOptions` (reference data at scale — server-side
 * `q` search, ids beyond the first page stay pickable). Tracks a working draft that
 * stays in sync with the upstream {@link selectedValues} until the user edits it, so
 * late-loading data populates correctly without clobbering in-flight edits. Errors are
 * surfaced by the global mutation onError (toast); this component only owns the
 * draft + save.
 */
export function AssignSection<V extends string>({
  title, options, loadOptions, selectedValues, selectedOptions, onSave, saving, placeholder, emptySelectedHint, successMessage,
}: AssignSectionProps<V>) {
  const { t } = useT();
  const [draft, setDraft] = useState<V[]>(selectedValues);
  // id→label bookkeeping for async mode (static mode resolves from `options`).
  const [labels, setLabels] = useState<Map<V, string>>(
    () => new Map((selectedOptions ?? []).map((o) => [o.value, o.label] as const)),
  );
  const interacted = useRef(false);

  // Sync draft with upstream until the user edits (covers late query load).
  useEffect(() => {
    if (interacted.current) return;
    setDraft(selectedValues);
    if (selectedOptions) {
      setLabels(new Map(selectedOptions.map((o) => [o.value, o.label] as const)));
    }
  }, [selectedValues, selectedOptions]);

  const debouncedLoad = useDebouncedLoadOptions(
    loadOptions ?? (async () => []),
  );

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

  const value = draft.map((id) => {
    if (loadOptions) return { value: id, label: labels.get(id) ?? String(id) };
    const opt = options?.find((o) => o.value === id);
    return { value: id, label: (opt?.label as string) ?? String(id) };
  });

  return (
    <DetailPanel title={title}>
      <SelectInput<V>
        isMulti
        isClearable
        {...(loadOptions
          ? { loadOptions: debouncedLoad, defaultOptions: true }
          : { options })}
        value={value}
        onChange={(next) => {
          interacted.current = true;
          const opts = ((next ?? []) as SelectOption<V>[]).slice();
          if (opts.length > 0) {
            setLabels((prev) => {
              const m = new Map(prev);
              for (const o of opts) m.set(o.value, o.label);
              return m;
            });
          }
          setDraft(opts.map((o) => o.value));
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
