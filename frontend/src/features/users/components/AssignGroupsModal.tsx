import { useState } from 'react';
import { useSetUserGroups, useUser } from '../hooks';
import { notify } from '../../../lib/notify';
import { Modal } from '../../../components/ui/Modal';
import { Button } from '../../../components/ui/Button';
import { GroupPicker } from '../../../components/pickers/GroupPicker';
import { useT } from '../../../lib/i18n';

/**
 * Opened from a list row (directory projection — counts only), so the current group set
 * is fetched here from the detail endpoint rather than carried on the row.
 */
export function AssignGroupsModal({ user, onClose }: { user: { id: string; email: string }; onClose: () => void }) {
  const { t } = useT();
  const { data: detail, isLoading: detailLoading } = useUser(user.id);
  const setGroups = useSetUserGroups();
  // null = untouched → follow the fetched detail; first interaction pins the selection.
  const [selected, setSelected] = useState<string[] | null>(null);
  const effective = selected ?? detail?.groups.map((g) => g.id) ?? [];

  const submit = async () => {
    try {
      await setGroups.mutateAsync({ id: user.id, data: { groupIds: effective } });
      notify.success(t('common.groupsUpdated'));
      onClose();
    } catch { /* global toast */ }
  };

  return (
    <Modal
      open
      title={t('users.groupsTitle', { email: user.email })}
      onClose={onClose}
      footer={<>
        <Button variant="ghost" onClick={onClose}>{t('common.cancel')}</Button>
        <Button variant="primary" loading={setGroups.isPending} disabled={detailLoading} onClick={submit}>{t('common.save')}</Button>
      </>}
    >
      {detailLoading ? (
        <div className="py-8 text-center text-sm text-muted">{t('users.loadingGroups')}</div>
      ) : (
        <GroupPicker
          isMulti
          values={effective}
          selectedOptions={(detail?.groups ?? []).map((g) => ({ value: g.id, label: g.name }))}
          onValuesChange={setSelected}
        />
      )}
    </Modal>
  );
}
