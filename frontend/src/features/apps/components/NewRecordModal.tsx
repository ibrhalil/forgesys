import { useState } from 'react';
import { useT } from '../../../lib/i18n';
import { Modal } from '../../../components/ui/Modal';
import { Button } from '../../../components/ui/Button';
import { TextField } from '../../../components/ui/Field';
import { SelectInput } from '../../../components/ui/SelectInput';
import { notify } from '../../../lib/notify';
import type { AppDetail } from '../types';
import { useCreateRecord } from '../hooks';
import { parseCellInput } from '../cellValue';
import { UserPicker } from './UserPicker';
import { RelationPicker } from './RelationPicker';

/**
 * Record create modal — one control per property: scalar types get plain inputs,
 * SELECT a dropdown, USER/RELATION their pickers. Only filled fields are sent;
 * every required property must carry a value (the backend enforces it too).
 */
export function NewRecordModal({ app, onClose }: { app: AppDetail; onClose: () => void }) {
  const { t } = useT();
  const create = useCreateRecord(app.id);
  const [draft, setDraft] = useState<Record<string, string>>({});
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const setValue = (propertyId: string, value: string) => {
    setDraft((prev) => ({ ...prev, [propertyId]: value }));
    setFieldErrors((prev) => ({ ...prev, [propertyId]: '' }));
  };

  const submit = async () => {
    const errors: Record<string, string> = {};
    const values: Record<string, string | number> = {};
    for (const prop of app.properties) {
      const raw = (draft[prop.id] ?? '').trim();
      if (raw === '') {
        if (prop.required) errors[prop.id] = t('apps.fieldRequired');
        continue;
      }
      if (prop.type === 'USER' || prop.type === 'RELATION') {
        // Picker-sourced ids — no client-side format check (server validates existence).
        values[prop.id] = raw;
        continue;
      }
      const parsed = parseCellInput(prop, raw);
      if (parsed === undefined || parsed === null) {
        errors[prop.id] = t('apps.invalidCellInput');
        continue;
      }
      values[prop.id] = parsed;
    }
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) return;
    try {
      await create.mutateAsync({ values });
      notify.success(t('apps.recordCreated'));
      onClose();
    } catch {
      /* global toast */
    }
  };

  return (
    <Modal
      open
      title={t('apps.newRecord')}
      onClose={onClose}
      size="lg"
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>{t('common.cancel')}</Button>
          <Button variant="primary" loading={create.isPending} onClick={submit}>{t('common.create')}</Button>
        </>
      }
    >
      <div className="grid gap-4 sm:grid-cols-2">
        {app.properties.map((prop) => {
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
            return (
              <UserPicker
                key={prop.id}
                label={label}
                value={draft[prop.id] ? draft[prop.id] : null}
                onChange={(v) => setValue(prop.id, v ?? '')}
                error={fieldErrors[prop.id] ?? null}
              />
            );
          }
          if (prop.type === 'RELATION') {
            return (
              <RelationPicker
                key={prop.id}
                property={prop}
                label={label}
                value={draft[prop.id] ? draft[prop.id] : null}
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
