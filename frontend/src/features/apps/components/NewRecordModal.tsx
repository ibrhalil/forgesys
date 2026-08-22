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

/**
 * Minimal record create modal — one control per property. USER/RELATION take a raw
 * id text input for now (pickers arrive in a later part). Only filled fields are
 * sent; every required property must carry a value (backend enforces it too).
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
          const isIdType = prop.type === 'USER' || prop.type === 'RELATION';
          return (
            <TextField
              key={prop.id}
              id={`record-${prop.id}`}
              label={label}
              type={prop.type === 'NUMBER' ? 'number' : prop.type === 'DATE' ? 'date' : 'text'}
              value={draft[prop.id] ?? ''}
              onChange={(e) => setValue(prop.id, e.target.value)}
              placeholder={isIdType ? t('apps.uuidPh') : undefined}
              hint={isIdType ? t('apps.uuidHint') : undefined}
              error={fieldErrors[prop.id] ?? null}
            />
          );
        })}
      </div>
    </Modal>
  );
}
