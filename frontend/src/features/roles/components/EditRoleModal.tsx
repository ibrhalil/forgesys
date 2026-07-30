import { useState } from 'react';
import { Button } from '../../../components/ui/Button';
import { Modal } from '../../../components/ui/Modal';
import { TextField } from '../../../components/ui/Field';
import { TextAreaField } from '../../../components/ui/TextArea';
import { notify, extractFieldErrors } from '../../../lib/notify';
import { useUpdateRole } from '../hooks';
import { useT } from '../../../lib/i18n';

/** Inline edit of a role's name/description (RoleDetailPage "Edit" action). */
export function EditRoleModal({
  roleId, name, description, onClose,
}: { roleId: string; name: string; description: string | null; onClose: () => void }) {
  const { t } = useT();
  const update = useUpdateRole();
  const [n, setN] = useState(name);
  const [d, setD] = useState(description ?? '');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const submit = async () => {
    setFieldErrors({});
    try {
      await update.mutateAsync({ id: roleId, data: { name: n, description: d || undefined } });
      notify.success(t('roles.updated'));
      onClose();
    } catch (e) {
      setFieldErrors(extractFieldErrors(e));
    }
  };

  return (
    <Modal
      open
      title={t('roles.editing', { name })}
      onClose={onClose}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>{t('common.cancel')}</Button>
          <Button variant="primary" loading={update.isPending} onClick={submit}>{t('common.save')}</Button>
        </>
      }
    >
      <div className="flex flex-col gap-4">
        <TextField label={t('common.name')} value={n} onChange={(e) => setN(e.target.value)} error={fieldErrors.name ?? null} required />
        <TextAreaField label={t('common.descriptionOptional')} value={d} onChange={(e) => setD(e.target.value)} error={fieldErrors.description ?? null} />
      </div>
    </Modal>
  );
}
