import { useState } from 'react';
import { useT } from '../../../lib/i18n';
import { Modal } from '../../../components/ui/Modal';
import { Button } from '../../../components/ui/Button';
import { TextField } from '../../../components/ui/Field';
import { TextAreaField } from '../../../components/ui/TextArea';
import { cn } from '../../../lib/cn';
import { extractFieldErrors, notify } from '../../../lib/notify';
import type { CustomApp } from '../types';
import { useCreateCustomApp, useUpdateCustomApp } from '../hooks';

/** Emoji shortlist — deliberately tiny; no icon library or upload pipeline. */
const APP_ICONS = [
  '📦', '📋', '🧾', '📊', '📈', '🗂️', '🛒', '🚚', '💰', '👥',
  '🏗️', '🧪', '🎯', '📅', '💡', '🔧', '📣', '🌱', '⚡', '🎓',
];

/** Create + edit customApp — name, optional emoji icon, description. */
export function CustomAppFormModal({
  customApp,
  projectId,
  onClose,
}: {
  customApp?: CustomApp;
  /** Target APPS container for creates (the project panel passes its own id). */
  projectId?: string;
  onClose: () => void;
}) {
  const { t } = useT();
  const create = useCreateCustomApp();
  const update = useUpdateCustomApp();
  const [name, setName] = useState(customApp?.name ?? '');
  const [icon, setIcon] = useState(customApp?.icon ?? '');
  const [description, setDescription] = useState(customApp?.description ?? '');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const submit = async () => {
    setFieldErrors({});
    try {
      if (customApp) {
        // Full PUT — the backend overwrites icon unconditionally, so an emptied
        // picker must be sent as an explicit null rather than omitted.
        await update.mutateAsync({ id: customApp.id, data: { name, description: description || undefined, icon: icon || null } });
        notify.success(t('customApps.updated'));
      } else {
        await create.mutateAsync({
          name,
          description: description || undefined,
          icon: icon || undefined,
          projectId: projectId || undefined,
        });
        notify.success(t('customApps.created'));
      }
      onClose();
    } catch (e) {
      setFieldErrors(extractFieldErrors(e));
    }
  };

  return (
    <Modal
      open
      title={customApp ? t('customApps.editing', { name: customApp.name }) : t('customApps.newTitle')}
      onClose={onClose}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>{t('common.cancel')}</Button>
          <Button variant="primary" loading={create.isPending || update.isPending} onClick={submit}>
            {customApp ? t('common.save') : t('common.create')}
          </Button>
        </>
      }
    >
      <div className="flex flex-col gap-4">
        <TextField
          id="customApp-name"
          label={t('common.name')}
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder={t('customApps.namePh')}
          error={fieldErrors.name ?? null}
          required
        />
        <div>
          <span className="text-xs font-medium uppercase tracking-wide text-muted">{t('customApps.iconLabel')}</span>
          <div className="mt-1.5 flex flex-wrap gap-1.5" role="group" aria-label={t('customApps.iconLabel')}>
            <button
              type="button"
              aria-pressed={icon === ''}
              aria-label={t('customApps.iconNone')}
              title={t('customApps.iconNone')}
              onClick={() => setIcon('')}
              className={cn(
                'flex h-9 w-9 items-center justify-center rounded-lg border text-sm transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-accent/60',
                icon === '' ? 'border-accent bg-accent/10 text-accent' : 'border-glass text-muted hover:bg-main/5',
              )}
            >
              —
            </button>
            {APP_ICONS.map((emoji) => (
              <button
                key={emoji}
                type="button"
                aria-pressed={icon === emoji}
                aria-label={emoji}
                onClick={() => setIcon(emoji)}
                className={cn(
                  'flex h-9 w-9 items-center justify-center rounded-lg border text-lg transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-accent/60',
                  icon === emoji ? 'border-accent bg-accent/10' : 'border-glass hover:bg-main/5',
                )}
              >
                <span aria-hidden>{emoji}</span>
              </button>
            ))}
          </div>
        </div>
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
