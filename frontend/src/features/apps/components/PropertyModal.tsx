import { useState } from 'react';
import { useT } from '../../../lib/i18n';
import { Modal } from '../../../components/ui/Modal';
import { Button } from '../../../components/ui/Button';
import { TextField } from '../../../components/ui/Field';
import { Toggle } from '../../../components/ui/Toggle';
import { SelectInput } from '../../../components/ui/SelectInput';
import type { SelectOption } from '../../../lib/select';
import { extractFieldErrors, notify } from '../../../lib/notify';
import type { AppProperty, PropertyType } from '../types';
import { useApp, useCreateProperty, useUpdateProperty } from '../hooks';
import { AppPicker } from '../../../components/pickers/AppPicker';

/** Order matters — mirrors the backend PropertyType catalog. FORMULA is listed but
 * disabled: the backend rejects it on create (deferred type). */
const PROPERTY_TYPES: PropertyType[] = ['TEXT', 'NUMBER', 'SELECT', 'DATE', 'USER', 'RELATION', 'FORMULA'];

export function PropertyModal({ appId, property, onClose }: { appId: string; property?: AppProperty; onClose: () => void }) {
  const { t } = useT();
  const create = useCreateProperty(appId);
  const update = useUpdateProperty(appId);
  const [name, setName] = useState(property?.name ?? '');
  const [type, setType] = useState<PropertyType>(property?.type ?? 'TEXT');
  const [required, setRequired] = useState(property?.required ?? false);
  const [position, setPosition] = useState(String(property?.position ?? 0));
  const [options, setOptions] = useState<string[]>(property?.config?.options ?? []);
  const [targetAppId, setTargetAppId] = useState<string | null>(property?.config?.targetAppId ?? null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [configError, setConfigError] = useState<string | null>(null);
  const isEdit = !!property;
  // RELATION target label seed — only edit mode knows the configured target.
  const { data: targetAppDetail } = useApp(isEdit ? property?.config?.targetAppId : undefined);

  const typeOptions: SelectOption<PropertyType>[] = PROPERTY_TYPES.map((v) => ({ value: v, label: t(`apps.type.${v}`) }));

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
      // The backend DTO keeps type @NotNull on create AND update (immutability
      // is a service-level rule — the SAME type must be re-sent on edit).
      type,
      config:
        type === 'SELECT'
          ? { options }
          : type === 'RELATION'
            ? { targetAppId: targetAppId! }
            : undefined,
      required,
      // Create appends at max+1 (no field shown); edit resends the explicit value.
      ...(isEdit ? { position: Number(position) || 0 } : {}),
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
          error={fieldErrors.type ?? null}
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
          <AppPicker
            label={t('apps.relationTarget')}
            value={targetAppId}
            valueLabel={targetAppDetail?.name}
            onChange={setTargetAppId}
            excludeIds={[appId]}
            isClearable
            error={configError}
          />
        )}
        <Toggle checked={required} onChange={setRequired} label={t('apps.requiredBadge')} />
        {isEdit && (
          <TextField
            id="property-position"
            label={t('apps.position')}
            type="number"
            min={0}
            max={9999}
            value={position}
            onChange={(e) => setPosition(e.target.value)}
          />
        )}
      </div>
    </Modal>
  );
}
