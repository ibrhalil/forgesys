import { useState } from 'react';
import { useT } from '../../../lib/i18n';
import { Modal } from '../../../components/ui/Modal';
import { Button } from '../../../components/ui/Button';
import { TextField } from '../../../components/ui/Field';
import { SelectInput } from '../../../components/ui/SelectInput';
import { notify } from '../../../lib/notify';
import type { CustomAppDetail, CustomAppRecord } from '../types';
import { useCreateRecord, usePatchRecord } from '../hooks';
import { buildRecordPatch, cellEditValue } from '../cellValue';
import { useValueResolvers } from '../valueLabels';
import { UserPicker } from '../../../components/pickers/UserPicker';
import { RelationPicker } from './RelationPicker';

/** Synthetic empty record — create mode diffs against it (every filled field is a change). */
const EMPTY_VALUES: Record<string, string | number | null> = {};

/**
 * Record form modal — one control per property: scalar types get plain inputs,
 * SELECT a dropdown, USER/RELATION their pickers. Create sends only filled
 * fields (required ones must carry a value); edit pre-fills the stored values
 * and PATCHes the partial-merge diff: changed keys only, `null` clears a cell,
 * untouched keys are never sent. Required properties cannot be emptied (the
 * backend rejects the clear — blocked client-side with an inline error).
 */
export function RecordFormModal({
  customApp,
  record,
  onClose,
}: {
  customApp: CustomAppDetail;
  /** Present = edit mode (prefilled draft, PATCH); absent = create mode (POST). */
  record?: CustomAppRecord;
  onClose: () => void;
}) {
  const { t } = useT();
  const isEdit = !!record;
  const create = useCreateRecord(customApp.id);
  const patch = usePatchRecord(customApp.id);
  const resolve = useValueResolvers(customApp, record ? [record] : []);
  const [draft, setDraft] = useState<Record<string, string>>(() =>
    isEdit && record
      ? Object.fromEntries(customApp.properties.map((p) => [p.id, cellEditValue(p, record)]))
      : {},
  );
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const setValue = (propertyId: string, value: string) => {
    setDraft((prev) => ({ ...prev, [propertyId]: value }));
    setFieldErrors((prev) => ({ ...prev, [propertyId]: '' }));
  };

  const submit = async () => {
    const errors: Record<string, string> = {};
    // Create diffs against empty values — filled fields are the payload, required
    // ones must be present. Edit diffs against the stored record — a `null` clear
    // of a required property is blocked inline (the backend would reject it).
    const base: CustomAppRecord = record ?? { ...({} as CustomAppRecord), values: EMPTY_VALUES };
    const { invalid, values } = buildRecordPatch(customApp.properties, base, draft);
    for (const id of invalid) errors[id] = t('customApps.invalidCellInput');
    for (const prop of customApp.properties) {
      if (!prop.required || errors[prop.id]) continue;
      const cleared = values[prop.id] === null;
      const missingOnCreate = !isEdit && !(prop.id in values);
      if (cleared || missingOnCreate) errors[prop.id] = cleared ? t('customApps.cannotClearRequired') : t('customApps.fieldRequired');
    }
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) return;

    try {
      if (isEdit && record) {
        // Empty diff = nothing touched — close without a request.
        if (Object.keys(values).length > 0) {
          await patch.mutateAsync({ recordId: record.id, data: { values } });
          notify.success(t('customApps.recordUpdated'));
        }
      } else {
        await create.mutateAsync({ values: values as Record<string, string | number> });
        notify.success(t('customApps.recordCreated'));
      }
      onClose();
    } catch {
      /* global toast */
    }
  };

  return (
    <Modal
      open
      title={isEdit ? t('customApps.editRecord') : t('customApps.newRecord')}
      onClose={onClose}
      size="lg"
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>{t('common.cancel')}</Button>
          <Button variant="primary" loading={create.isPending || patch.isPending} onClick={submit}>
            {isEdit ? t('common.save') : t('common.create')}
          </Button>
        </>
      }
    >
      <div className="grid gap-4 sm:grid-cols-2">
        {customApp.properties.map((prop) => {
          const label = `${prop.name}${prop.required ? ' *' : ''}`;
          if (prop.type === 'SELECT') {
            const options = (prop.config?.options ?? []).map((o) => ({ value: o, label: o }));
            return (
              <SelectInput
                key={prop.id}
                id={`record-${prop.id}`}
                label={label}
                options={options}
                value={options.find((o) => o.value === draft[prop.id]) ?? null}
                onChange={(o) => setValue(prop.id, (o as { value: string } | null)?.value ?? '')}
                isClearable
                error={fieldErrors[prop.id] ?? null}
              />
            );
          }
          if (prop.type === 'USER') {
            const value = draft[prop.id] ?? '';
            return (
              <UserPicker
                key={prop.id}
                label={label}
                value={value || null}
                valueLabel={isEdit && value ? resolve(prop, record!) || undefined : undefined}
                onChange={(v) => setValue(prop.id, v ?? '')}
                error={fieldErrors[prop.id] ?? null}
              />
            );
          }
          if (prop.type === 'RELATION') {
            const value = draft[prop.id] ?? '';
            return (
              <RelationPicker
                key={prop.id}
                property={prop}
                label={label}
                value={value || null}
                valueLabel={isEdit && value ? resolve(prop, record!) || undefined : undefined}
                onChange={(v) => setValue(prop.id, v ?? '')}
                error={fieldErrors[prop.id] ?? null}
              />
            );
          }
          return (
            <TextField
              key={prop.id}
              id={`record-${prop.id}`}
              label={label}
              type={prop.type === 'NUMBER' ? 'number' : prop.type === 'DATE' ? 'date' : 'text'}
              value={draft[prop.id] ?? ''}
              onChange={(e) => setValue(prop.id, e.target.value)}
              error={fieldErrors[prop.id] ?? null}
            />
          );
        })}
      </div>
    </Modal>
  );
}
