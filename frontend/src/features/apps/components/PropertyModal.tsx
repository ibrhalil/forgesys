import { useState } from 'react';
import { useT } from '../../../lib/i18n';
import { Modal } from '../../../components/ui/Modal';
import { Button } from '../../../components/ui/Button';
import { TextField } from '../../../components/ui/Field';
import { Toggle } from '../../../components/ui/Toggle';
import { SelectInput } from '../../../components/ui/SelectInput';
import type { SelectOption } from '../../../lib/select';
import { extractFieldErrors, notify } from '../../../lib/notify';
import type { App, AppProperty, PropertyType } from '../types';
import { useApps, useCreateProperty, useUpdateProperty } from '../hooks';

/** Order matters — mirrors the backend PropertyType catalog. FORMULA is listed but
 * disabled: the backend rejects it on create (deferred type). */
const PROPERTY_TYPES: PropertyType[] = ['TEXT', 'NUMBER', 'SELECT', 'DATE', 'USER', 'RELATION', 'FORMULA'];

export function PropertyModal({ appId, property, onClose }: { appId: string; property?: AppProperty; onClose: () => void }) {
  const { t } = useT();
  const create = useCreateProperty(appId);
  const update = useUpdateProperty(appId);
  // RELATION target picker — apps list (cross-feature hook, hook-level import is allowed).
  const { data: appsData } = useApps({ size: 100, sorts: [{ field: 'name', dir: 'asc' }] });

  const [name, setName] = useState(property?.name ?? '');
  const [type, setType] = useState<PropertyType>(property?.type ?? 'TEXT');
  const [required, setRequired] = useState(property?.required ?? false);
  const [position, setPosition] = useState(String(property?.position ?? 0));
  const [options, setOptions] = useState<string[]>(property?.config?.options ?? []);
  const [targetAppId, setTargetAppId] = useState<string | null>(property?.config?.targetAppId ?? null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [configError, setConfigError] = useState<string | null>(null);
  const isEdit = !!property;

  const typeOptions: SelectOption<PropertyType>[] = PROPERTY_TYPES.map((v) => ({ value: v, label: t(`apps.type.${v}`) }));
  const targetOptions: SelectOption<string>[] = (appsData?.items ?? [])
    .filter((a: App) => a.id !== appId)
    .map((a: App) => ({ value: a.id, label: a.name }));

  const submit = async () => {
    setFieldErrors({});
    setConfigError(null);
    // Type-scoped config pre-validation (the backend re-validates; this keeps the
    // error inline next to the offending control instead of a generic toast).
    if (type === 'SELECT' && options.length === 0) {
      setConfigError(t('apps.optionsRequired'));
      return;
    }
    if (type === 'RELATION' && !targetAppId) {
      setConfigError(t('apps.relationRequired'));
      return;
    }
    const data = {
      name,
      // Immutable after create — omitted on edit (backend rejects type changes).
      ...(isEdit ? {} : { type }),
      config:
        type === 'SELECT'
          ? { options }
          : type === 'RELATION'
            ? { targetAppId: targetAppId! }
            : undefined,
      required,
      position: Number(position) || 0,
    };
    try {
      if (isEdit) {
        await update.mutateAsync({ propertyId: property.id, data });
        notify.success(t('apps.propertyUpdated'));
      } else {
        await create.mutateAsync(data);
        notify.success(t('apps.propertyCreated'));
      }
      onClose();
    } catch (e) {
      setFieldErrors(extractFieldErrors(e));
    }
  };

  return (
    <Modal
      open
      title={isEdit ? t('apps.propertyEditing', { name: property.name }) : t('apps.propertyNew')}
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
          id="property-name"
          label={t('common.name')}
          value={name}
          onChange={(e) => setName(e.target.value)}
          error={fieldErrors.name ?? null}
          required
        />
        <SelectInput<PropertyType>
          id="property-type"
          label={t('apps.type')}
          options={typeOptions}
          value={typeOptions.find((o) => o.value === type) ?? null}
          onChange={(o) => setType((o as SelectOption<PropertyType>)?.value ?? 'TEXT')}
          isDisabled={isEdit}
          isOptionDisabled={(o) => o.value === 'FORMULA'}
          hint={isEdit ? t('apps.typeImmutable') : t('apps.formulaDisabled')}
        />
        {type === 'SELECT' && (
          <SelectInput<string>
            id="property-options"
            label={t('apps.optionsLabel')}
            placeholder={t('apps.optionsPh')}
            options={options.map((o) => ({ value: o, label: o }))}
            value={options.map((o) => ({ value: o, label: o }))}
            onChange={(next) => setOptions(((next ?? []) as SelectOption<string>[]).map((o) => o.value))}
            isMulti
            creatable
            error={configError}
          />
        )}
        {type === 'RELATION' && (
          <SelectInput<string>
            id="property-target"
            label={t('apps.relationTarget')}
            options={targetOptions}
            value={targetOptions.find((o) => o.value === targetAppId) ?? null}
            onChange={(o) => setTargetAppId((o as SelectOption<string> | null)?.value ?? null)}
            isClearable
            error={configError}
          />
        )}
        <Toggle checked={required} onChange={setRequired} label={t('apps.requiredBadge')} />
        <TextField
          id="property-position"
          label={t('apps.position')}
          type="number"
          min={0}
          max={9999}
          value={position}
          onChange={(e) => setPosition(e.target.value)}
        />
      </div>
    </Modal>
  );
}
