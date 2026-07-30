import { useState } from 'react';
import { useResetPassword } from '../hooks';
import { notify, extractFieldErrors } from '../../../lib/notify';
import { Modal } from '../../../components/ui/Modal';
import { Button } from '../../../components/ui/Button';
import { TextField } from '../../../components/ui/Field';
import { useT } from '../../../lib/i18n';

export function ResetPasswordModal({ user, onClose }: { user: { id: string; email: string }; onClose: () => void }) {
  const { t } = useT();
  const reset = useResetPassword();
  const [newPassword, setNewPassword] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const submit = async () => {
    setFieldErrors({});
    try {
      await reset.mutateAsync({ id: user.id, data: { newPassword } });
      notify.success(t('users.passwordReset'));
      onClose();
    } catch (e) {
      setFieldErrors(extractFieldErrors(e));
    }
  };

  return (
    <Modal
      open
      title={t('users.resetTitle', { email: user.email })}
      onClose={onClose}
      size="sm"
      footer={<>
        <Button variant="ghost" onClick={onClose}>{t('common.cancel')}</Button>
        <Button variant="primary" loading={reset.isPending} onClick={submit}>{t('common.reset')}</Button>
      </>}
    >
      <div className="flex flex-col gap-3">
        <TextField label={t('common.newPassword')} type="password" value={newPassword} onChange={(e) => setNewPassword(e.target.value)} placeholder={t('common.min8')} hint={t('common.min8')} error={fieldErrors.newPassword ?? null} required />
      </div>
    </Modal>
  );
}
