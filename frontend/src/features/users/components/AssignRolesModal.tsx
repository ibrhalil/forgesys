import { useState } from 'react';
import type { Role } from '../../roles/types';
import { useSetUserRoles, useUser } from '../hooks';
import { useRoles } from '../../roles/hooks';
import { notify } from '../../../lib/notify';
import { Modal } from '../../../components/ui/Modal';
import { Button } from '../../../components/ui/Button';
import { CheckboxList, type CheckboxItem } from '../../../components/ui/CheckboxList';
import { useT } from '../../../lib/i18n';

/**
 * Opened from a list row (directory projection — counts only), so the current role set
 * is fetched here from the detail endpoint rather than carried on the row.
 */
export function AssignRolesModal({ user, onClose }: { user: { id: string; email: string }; onClose: () => void }) {
  const { t } = useT();
  const { data: rolesData, isLoading } = useRoles({ size: 100 });
  const { data: detail, isLoading: detailLoading } = useUser(user.id);
  const setRoles = useSetUserRoles();
  // null = untouched → follow the fetched detail; first interaction pins the selection.
  const [selected, setSelected] = useState<string[] | null>(null);
  const effective = selected ?? detail?.roles.map((r) => r.id) ?? [];

  const items: CheckboxItem[] = (rolesData?.items ?? []).map((r: Role) => ({ id: r.id, label: r.name, description: r.description }));

  const submit = async () => {
    try {
      await setRoles.mutateAsync({ id: user.id, data: { roleIds: effective } });
      notify.success(t('common.rolesUpdated'));
      onClose();
    } catch { /* global toast */ }
  };

  return (
    <Modal
      open
      title={t('users.rolesTitle', { email: user.email })}
      onClose={onClose}
      footer={<>
        <Button variant="ghost" onClick={onClose}>{t('common.cancel')}</Button>
        <Button variant="primary" loading={setRoles.isPending} disabled={detailLoading} onClick={submit}>{t('common.save')}</Button>
      </>}
    >
      {isLoading || detailLoading ? (
        <div className="py-8 text-center text-sm text-muted">{t('users.loadingRoles')}</div>
      ) : (
        <CheckboxList items={items} selectedIds={effective} onChange={setSelected} emptyMessage={t('users.noRolesDefined')} />
      )}
    </Modal>
  );
}
