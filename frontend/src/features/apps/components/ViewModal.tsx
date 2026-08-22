import { useState } from 'react';
import { useT } from '../../../lib/i18n';
import { Modal } from '../../../components/ui/Modal';
import { Button } from '../../../components/ui/Button';
import { TextField } from '../../../components/ui/Field';
import { SelectInput } from '../../../components/ui/SelectInput';
import type { SelectOption } from '../../../lib/select';
import { extractFieldErrors, notify } from '../../../lib/notify';
import type { AppProperty, AppView, AppViewRequest, ViewType } from '../types';
import { useCreateView, useUpdateView } from '../hooks';

const VIEW_TYPES: ViewType[] = ['TABLE', 'BOARD', 'CALENDAR', 'GALLERY', 'LIST'];

/**
 * View create/edit modal — name + type + the type-specific anchor (BOARD `groupBy`
 * over SELECT properties, CALENDAR `dateProperty` over DATE properties). Existing
 * filters/sorts are preserved across edits (anchors are rebuilt for the chosen
 * type, so switching types never leaves a stale anchor behind — the backend
 * revalidates the whole config on PUT).
 */
export function ViewModal({
  appId,
  properties,
  view,
  onCreated,
  onClose,
}: {
  appId: string;
  properties: AppProperty[];
  view?: AppView;
  /** Notified with the created view so the caller can select it. */
  onCreated?: (view: AppView) => void;
  onClose: () => void;
}) {
  const { t } = useT();
  const create = useCreateView(appId);
  const update = useUpdateView(appId);
  const isEdit = !!view;

  const [name, setName] = useState(view?.name ?? '');
  const [type, setType] = useState<ViewType>(view?.type ?? 'TABLE');
  const [groupBy, setGroupBy] = useState<string | null>(view?.config?.groupBy ?? null);
  const [dateProperty, setDateProperty] = useState<string | null>(view?.config?.dateProperty ?? null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [configError, setConfigError] = useState<string | null>(null);

  const typeOptions: SelectOption<ViewType>[] = VIEW_TYPES.map((v) => ({
    value: v,
    label: t(`apps.viewType.${v}`),
  }));
  const groupByOptions: SelectOption<string>[] = properties
    .filter((p) => p.type === 'SELECT')
    .map((p) => ({ value: p.id, label: p.name }));
  const datePropertyOptions: SelectOption<string>[] = properties
    .filter((p) => p.type === 'DATE')
    .map((p) => ({ value: p.id, label: p.name }));

  const submit = async () => {
    setFieldErrors({});
    setConfigError(null);
    // Client-side pre-validation mirrors the backend AppViewConfigValidator rules;
    // the server revalidates and any business error surfaces as a global toast.
    if (!name.trim()) {
      setFieldErrors({ name: t('apps.fieldRequired') });
      return;
    }
    if (type === 'BOARD' && !groupBy) {
      setConfigError(t('apps.viewGroupByRequired'));
      return;
    }
    if (type === 'CALENDAR' && !dateProperty) {
      setConfigError(t('apps.viewDatePropertyRequired'));
      return;
    }
    const data: AppViewRequest = {
      name: name.trim(),
      type,
      config: {
        ...(view?.config?.filters ? { filters: view.config.filters } : {}),
        ...(view?.config?.sorts ? { sorts: view.config.sorts } : {}),
        ...(type === 'BOARD' && groupBy ? { groupBy } : {}),
        ...(type === 'CALENDAR' && dateProperty ? { dateProperty } : {}),
      },
      // Full PUT — resend the current position, the backend resets a missing one to 0.
      position: view?.position ?? 0,
    };
    try {
      if (isEdit && view) {
        await update.mutateAsync({ viewId: view.id, data });
        notify.success(t('apps.viewUpdated'));
      } else {
        const created = await create.mutateAsync(data);
        onCreated?.(created);
        notify.success(t('apps.viewCreated'));
      }
      onClose();
    } catch (e) {
      setFieldErrors(extractFieldErrors(e));
    }
  };

  return (
    <Modal
      open
      title={isEdit ? t('apps.viewEditing', { name: view.name }) : t('apps.viewNew')}
      onClose={onClose}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>{t('common.cancel')}</Button>
          <Button variant="primary" loading={create.isPending || update.isPending} onClick={submit}>
            {isEdit ? t('common.save') : t('common.create')}
          </Button>
        </>
      }
    >
      <div className="flex flex-col gap-4">
        <TextField
          id="view-name"
          label={t('common.name')}
          value={name}
          onChange={(e) => setName(e.target.value)}
          maxLength={150}
          error={fieldErrors.name ?? null}
          required
        />
        <SelectInput<ViewType>
          id="view-type"
          label={t('apps.type')}
          options={typeOptions}
          value={typeOptions.find((o) => o.value === type) ?? null}
          onChange={(o) => setType((o as SelectOption<ViewType>)?.value ?? 'TABLE')}
        />
        {type === 'BOARD' && (
          <SelectInput<string>
            id="view-group-by"
            label={t('apps.viewGroupBy')}
            options={groupByOptions}
            value={groupByOptions.find((o) => o.value === groupBy) ?? null}
            onChange={(o) => setGroupBy((o as SelectOption<string> | null)?.value ?? null)}
            error={configError}
            hint={groupByOptions.length === 0 ? t('apps.viewNoSelectProperty') : t('apps.viewGroupByHint')}
          />
        )}
        {type === 'CALENDAR' && (
          <SelectInput<string>
            id="view-date-property"
            label={t('apps.viewDateProperty')}
            options={datePropertyOptions}
            value={datePropertyOptions.find((o) => o.value === dateProperty) ?? null}
            onChange={(o) => setDateProperty((o as SelectOption<string> | null)?.value ?? null)}
            error={configError}
            hint={datePropertyOptions.length === 0 ? t('apps.viewNoDateProperty') : t('apps.viewDatePropertyHint')}
          />
        )}
      </div>
    </Modal>
  );
}
