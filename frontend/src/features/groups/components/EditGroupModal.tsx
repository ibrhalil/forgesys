import { useState } from 'react';
import { Button } from '../../../components/ui/Button';
import { Modal } from '../../../components/ui/Modal';
import { TextField } from '../../../components/ui/Field';
import { TextAreaField } from '../../../components/ui/TextArea';
import { Toggle } from '../../../components/ui/Toggle';
import { notify, extractFieldErrors } from '../../../lib/notify';
import { useUpdateGroup } from '../hooks';
import { useT } from '../../../lib/i18n';

/** Inline edit of a group's name/description/active flag (GroupDetailPage "Edit" action). */
export function EditGroupModal({
  groupId, name, description, active, onClose,
}: { groupId: string; name: string; description: string | null; active: boolean; onClose: () => void }) {
  const { t } = useT();
  const update = useUpdateGroup();
  const [n, setN] = useState(name);
  const [d, setD] = useState(description ?? '');
  const [a, setA] = useState(active);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const submit = async () => {
    setFieldErrors({});
    try {
      await update.mutateAsync({ id: groupId, data: { name: n, description: d || undefined, active: a } });
      notify.success(t('groups.updated'));
      onClose();
    } catch (e) {
      setFieldErrors(extractFieldErrors(e));
    }
  };

  return (
    <Modal
      open
      title={t('groups.editing', { name })}
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
        <Toggle checked={a} onChange={setA} label={t('common.activeLbl')} />
      </div>
    </Modal>
  );
}
