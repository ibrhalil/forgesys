import { useState } from 'react';
import { useT } from '../../../lib/i18n';
import { Modal } from '../../../components/ui/Modal';
import { Button } from '../../../components/ui/Button';
import { TextField } from '../../../components/ui/Field';
import { Toggle } from '../../../components/ui/Toggle';
import { SelectInput } from '../../../components/ui/SelectInput';
import type { SelectOption } from '../../../lib/select';
import { extractFieldErrors, notify } from '../../../lib/notify';
import type { CustomAppProperty, PropertyType } from '../types';
import { useCustomApp, useCreateProperty, useUpdateProperty } from '../hooks';
import { CustomAppPicker } from '../../../components/pickers/CustomAppPicker';

/** Order matters — mirrors the backend PropertyType catalog. FORMULA is listed but
 * disabled: the backend rejects it on create (deferred type). */
const PROPERTY_TYPES: PropertyType[] = ['TEXT', 'NUMBER', 'SELECT', 'DATE', 'USER', 'RELATION', 'FORMULA'];

export function PropertyModal({ customAppId, property, onClose }: { customAppId: string; property?: CustomAppProperty; onClose: () => void }) {
  const { t } = useT();
  const create = useCreateProperty(customAppId);
  const update = useUpdateProperty(customAppId);
  const [name, setName] = useState(property?.name ?? '');
  const [type, setType] = useState<PropertyType>(property?.type ?? 'TEXT');
  const [required, setRequired] = useState(property?.required ?? false);
  const [position, setPosition] = useState(String(property?.position ?? 0));
  const [options, setOptions] = useState<string[]>(property?.config?.options ?? []);
  const [targetCustomAppId, setTargetAppId] = useState<string | null>(property?.config?.targetCustomAppId ?? null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [configError, setConfigError] = useState<string | null>(null);
  const isEdit = !!property;
  // RELATION target label seed — only edit mode knows the configured target.
  const { data: targetAppDetail } = useCustomApp(isEdit ? property?.config?.targetCustomAppId : undefined);

  const typeOptions: SelectOption<PropertyType>[] = PROPERTY_TYPES.map((v) => ({ value: v, label: t(`customApps.type.${v}`) }));

  const submit = async () => {
    setFieldErrors({});
    setConfigError(null);
    // Type-scoped config pre-validation (the backend re-validates; this keeps the
    // error inline next to the offending control instead of a generic toast).
    if (type === 'SELECT' && options.length === 0) {
      setConfigError(t('customApps.optionsRequired'));
      return;
    }
    if (type === 'RELATION' && !targetCustomAppId) {
      setConfigError(t('customApps.relationRequired'));
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
            ? { targetCustomAppId: targetCustomAppId! }
            : undefined,
      required,
      // Create appends at max+1 (no field shown); edit resends the explicit value.
      ...(isEdit ? { position: Number(position) || 0 } : {}),
    };
    try {
      if (isEdit) {
        await update.mutateAsync({ propertyId: property.id, data });
        notify.success(t('customApps.propertyUpdated'));
      } else {
        await create.mutateAsync(data);
        notify.success(t('customApps.propertyCreated'));
      }
      onClose();
    } catch (e) {
      setFieldErrors(extractFieldErrors(e));
    }
  };

  return (
    <Modal
      open
      title={isEdit ? t('customApps.propertyEditing', { name: property.name }) : t('customApps.propertyNew')}
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
          label={t('customApps.type')}
          options={typeOptions}
          value={typeOptions.find((o) => o.value === type) ?? null}
          onChange={(o) => setType((o as SelectOption<PropertyType>)?.value ?? 'TEXT')}
          isDisabled={isEdit}
          isOptionDisabled={(o) => o.value === 'FORMULA'}
          hint={isEdit ? t('customApps.typeImmutable') : t('customApps.formulaDisabled')}
          error={fieldErrors.type ?? null}
        />
        {type === 'SELECT' && (
          <SelectInput<string>
            id="property-options"
            label={t('customApps.optionsLabel')}
            placeholder={t('customApps.optionsPh')}
            options={options.map((o) => ({ value: o, label: o }))}
            value={options.map((o) => ({ value: o, label: o }))}
            onChange={(next) => setOptions(((next ?? []) as SelectOption<string>[]).map((o) => o.value))}
            isMulti
            creatable
            error={configError}
          />
        )}
        {type === 'RELATION' && (
          <CustomAppPicker
            label={t('customApps.relationTarget')}
            value={targetCustomAppId}
            valueLabel={targetAppDetail?.name}
            onChange={setTargetAppId}
            excludeIds={[customAppId]}
            isClearable
            error={configError}
          />
        )}
        <Toggle checked={required} onChange={setRequired} label={t('customApps.requiredBadge')} />
        {isEdit && (
          <TextField
            id="property-position"
            label={t('customApps.position')}
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
