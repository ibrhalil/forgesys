import { useState } from 'react';
import { useT } from '../../../lib/i18n';
import { Modal } from '../../../components/ui/Modal';
import { Button } from '../../../components/ui/Button';
import { TextField } from '../../../components/ui/Field';
import { TextAreaField } from '../../../components/ui/TextArea';
import { extractFieldErrors, notify } from '../../../lib/notify';
import type { App } from '../types';
import { useCreateApp, useUpdateApp } from '../hooks';

/** Create + edit app — name and description only (icon input arrives with view management). */
export function AppFormModal({ app, onClose }: { app?: App; onClose: () => void }) {
  const { t } = useT();
  const create = useCreateApp();
  const update = useUpdateApp();
  const [name, setName] = useState(app?.name ?? '');
  const [description, setDescription] = useState(app?.description ?? '');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const submit = async () => {
    setFieldErrors({});
    const data = { name, description: description || undefined };
    try {
      if (app) {
        await update.mutateAsync({ id: app.id, data });
        notify.success(t('apps.updated'));
      } else {
        await create.mutateAsync(data);
        notify.success(t('apps.created'));
      }
      onClose();
    } catch (e) {
      setFieldErrors(extractFieldErrors(e));
    }
  };

  return (
    <Modal
      open
      title={app ? t('apps.editing', { name: app.name }) : t('apps.newTitle')}
      onClose={onClose}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>{t('common.cancel')}</Button>
          <Button variant="primary" loading={create.isPending || update.isPending} onClick={submit}>
            {app ? t('common.save') : t('common.create')}
          </Button>
        </>
      }
    >
      <div className="flex flex-col gap-4">
        <TextField
          id="app-name"
          label={t('common.name')}
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder={t('apps.namePh')}
          error={fieldErrors.name ?? null}
          required
        />
        <TextAreaField
          label={t('common.descriptionOptional')}
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          error={fieldErrors.description ?? null}
        />
      </div>
    </Modal>
  );
}
