import { useState, type FormEvent } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { LuEllipsisVertical, LuKeyRound, LuListChecks, LuMailCheck, LuMonitor, LuPencil, LuTrash2, LuLockOpen } from 'react-icons/lu';
import {
  useUser, useUserEffectivePermissions, useUserActivity, useCreateUser, useUpdateUser,
  useSetUserRoles, useSetUserGroups, useDeleteUser, useUnlockUser, useResendVerification,
} from './hooks';
import { isLocked } from './types';
import { formatDateTime } from '../../lib/format';
import { useAuthStore } from '../../store/authStore';
import { notify, extractFieldErrors } from '../../lib/notify';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { TextField } from '../../components/ui/Field';
import { RolePicker } from '../../components/pickers/RolePicker';
import { GroupPicker } from '../../components/pickers/GroupPicker';
import { RowMenu } from '../../components/ui/RowMenu';
import { Toggle } from '../../components/ui/Toggle';
import { DetailPanel, DetailField } from '../../components/detail/DetailPanel';
import { Page } from '../../components/Page';
import { PermissionListModal } from '../../components/detail/PermissionListModal';
import { DetailLoading, DetailNotFound } from '../../components/detail/DetailFallback';
import { ResetPasswordModal } from './components/ResetPasswordModal';
import { PERMISSIONS } from '../../lib/permissions';
import { useT } from '../../lib/i18n';

/**
 * The single user screen: create (`/users/new`) and view/edit (`/users/:userId`) in
 * one page. Edit-mode save is diff-based and sequential (NOT atomic): only changed
 * parts are sent (identity update / role set / group set), a mid-sequence failure
 * keeps the drafts intact and re-saving is idempotent. Drafts are seeded once in
 * `startEdit` — never from a `user` effect — so a background refetch (a save
 * invalidates ['users']) cannot clobber in-progress edits.
 */
export function UserDetailPage() {
  const { t } = useT();
  const navigate = useNavigate();
  const { userId } = useParams<{ userId: string }>();
  const isCreate = !userId;

  const { data: user, isLoading } = useUser(isCreate ? undefined : userId);
  const { data: effectivePerms } = useUserEffectivePermissions(isCreate ? undefined : userId);
  const { data: activity } = useUserActivity(isCreate ? undefined : userId);

  const create = useCreateUser();
  const update = useUpdateUser();
  const setRoles = useSetUserRoles();
  const setGroups = useSetUserGroups();
  const del = useDeleteUser();
  const unlockUser = useUnlockUser();
  const resendVerification = useResendVerification();
  const currentUserId = useAuthStore((s) => s.user?.id);
  const canWrite = useAuthStore((s) => s.hasAuthority(PERMISSIONS.USER_WRITE));
  const canDelete = useAuthStore((s) => s.hasAuthority(PERMISSIONS.USER_DELETE));

  const [editing, setEditing] = useState(false);
  const [resetting, setResetting] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [unlocking, setUnlocking] = useState(false);
  const [showPerms, setShowPerms] = useState(false);

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [username, setUsername] = useState('');
  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [enabled, setEnabled] = useState(true);
  const [roleIds, setRoleIds] = useState<string[]>([]);
  const [groupIds, setGroupIds] = useState<string[]>([]);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const saving = create.isPending || update.isPending || setRoles.isPending || setGroups.isPending;

  const startEdit = () => {
    if (!user) return;
    setFirstName(user.firstName ?? '');
    setLastName(user.lastName ?? '');
    setEnabled(user.enabled);
    setRoleIds(user.roles.map((r) => r.id));
    setGroupIds(user.groups.map((g) => g.id));
    setFieldErrors({});
    setEditing(true);
  };

  const cancelEdit = () => {
    if (user) {
      setFirstName(user.firstName ?? '');
      setLastName(user.lastName ?? '');
      setEnabled(user.enabled);
      setRoleIds(user.roles.map((r) => r.id));
      setGroupIds(user.groups.map((g) => g.id));
    }
    setFieldErrors({});
    setEditing(false);
  };

  /** Set equality (order-insensitive) — compares a draft against the persisted ids. */
  const sameSet = (a: string[], b: string[]) =>
    a.length === b.length && a.every((id) => b.includes(id));

  const handleSubmit = async (e?: FormEvent) => {
    e?.preventDefault();
    const errors: Record<string, string> = {};
    if (isCreate) {
      if (!email.trim()) errors.email = t('common.fieldRequired');
      if (password.length < 8) errors.password = t('common.min8');
      if (Object.keys(errors).length > 0) {
        setFieldErrors(errors);
        return;
      }
    }
    setFieldErrors({});
    try {
      if (isCreate) {
        const created = await create.mutateAsync({
          email: email.trim(),
          password,
          username: username || undefined,
          firstName: firstName || undefined,
          lastName: lastName || undefined,
          enabled,
          roleIds: roleIds.length ? roleIds : undefined,
          groupIds: groupIds.length ? groupIds : undefined,
        });
        notify.success(t('users.created'));
        navigate(`/users/${created.id}`, { replace: true });
        return;
      }
      if (!user) return;
      // Diff-based sequential save — see the component docblock.
      const identityDirty =
        (user.firstName ?? '') !== firstName ||
        (user.lastName ?? '') !== lastName ||
        user.enabled !== enabled;
      const rolesDirty = !sameSet(user.roles.map((r) => r.id), roleIds);
      const groupsDirty = !sameSet(user.groups.map((g) => g.id), groupIds);

      if (!identityDirty && !rolesDirty && !groupsDirty) {
        setEditing(false);
        return;
      }
      if (identityDirty) {
        await update.mutateAsync({ id: user.id, data: { firstName, lastName, enabled } });
      }
      if (rolesDirty) {
        await setRoles.mutateAsync({ id: user.id, data: { roleIds } });
      }
      if (groupsDirty) {
        await setGroups.mutateAsync({ id: user.id, data: { groupIds } });
      }
      notify.success(t('users.updated'));
      setEditing(false);
    } catch (err) {
      setFieldErrors(extractFieldErrors(err));
    }
  };

  if (!isCreate && isLoading) return <DetailLoading message={t('users.loadingUser')} />;
  if (!isCreate && !isLoading && !user) {
    return <DetailNotFound message={t('users.notFound')} backLabel={t('users.backToUsers')} backTo="/users" />;
  }

  const fullName = [user?.firstName, user?.lastName].filter(Boolean).join(' ');
  const heading = isCreate ? t('users.formNew') : (user?.email ?? '');
  const formActive = isCreate || editing;

  return (
    <Page
      breadcrumb={[
        { label: t('nav.identity') },
        { label: t('nav.users'), to: '/users' },
        { label: isCreate ? t('users.formNew') : (user?.email ?? '') },
      ]}
      title={heading}
      actions={!isCreate && user ? (
        <>
          {canWrite && !editing && (
            <Button size="sm" variant="ghost" onClick={startEdit}>
              <LuPencil className="h-3.5 w-3.5" />
              {t('common.edit')}
            </Button>
          )}
          {/* Hidden while editing — a dirty form must not trigger parallel mutations. */}
          {!formActive && (
            <RowMenu
              ariaLabel={t('common.actions')}
              icon={LuEllipsisVertical}
              items={[
                ...(canWrite
                  ? [
                      { label: t('users.passwordBtn'), onClick: () => setResetting(true), icon: LuKeyRound },
                      { label: t('nav.sessions'), onClick: () => navigate(`/admin/users/${user.id}/sessions`), icon: LuMonitor },
                      // Optional email verification: only meaningful pre-verification.
                      ...(user.emailVerified ? [] : [{
                        label: t('users.resendVerification'),
                        onClick: async () => {
                          try {
                            await resendVerification.mutateAsync(user.id);
                            notify.success(t('users.verificationResent'));
                          } catch {
                            /* rejection is reported by the global mutation toast */
                          }
                        },
                        icon: LuMailCheck,
                      }]),
                     ]
                   : []),
                // Unlock only while an active lock window is running.
                ...(canWrite && isLocked(user)
                  ? [{ label: t('users.unlock'), onClick: () => setUnlocking(true), icon: LuLockOpen }]
                  : []),
                // iam:user:read gates the page — every viewer may open the modal.
                { label: t('users.viewEffectivePerms'), onClick: () => setShowPerms(true), icon: LuListChecks },
                // Self-delete is rejected by the backend (409) — omit on the actor's own page.
                ...(canDelete && user.id !== currentUserId
                  ? [{ label: t('common.delete'), onClick: () => setDeleting(true), icon: LuTrash2, danger: true }]
                  : []),
              ]}
            />
          )}
        </>
      ) : undefined}
    >

      <DetailPanel title={t('users.identitySection')}>
        {formActive ? (
          <>
            <form className="grid grid-cols-1 gap-4 sm:grid-cols-2" onSubmit={handleSubmit} noValidate>
            {isCreate ? (
              <>
                <TextField
                  label={t('common.email')}
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder={t('users.emailPh')}
                  error={fieldErrors.email ?? null}
                  required
                />
                <TextField
                  label={t('common.password')}
                  type="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder={t('common.min8')}
                  hint={t('common.min8')}
                  error={fieldErrors.password ?? null}
                  required
                />
                <TextField
                  label={t('users.usernameOptional')}
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  placeholder={t('users.usernamePh')}
                  error={fieldErrors.username ?? null}
                />
              </>
            ) : (
              <>
                {/* Identity-bearing fields are immutable by contract — disabled, bound to the persisted user. */}
                <TextField
                  label={t('common.email')}
                  type="email"
                  value={user?.email ?? ''}
                  disabled
                />
                <TextField
                  label={t('common.username')}
                  value={user?.username ?? ''}
                  disabled
                />
              </>
            )}
            <TextField
              label={t('common.firstName')}
              value={firstName}
              onChange={(e) => setFirstName(e.target.value)}
              error={fieldErrors.firstName ?? null}
            />
            <TextField
              label={t('common.lastName')}
              value={lastName}
              onChange={(e) => setLastName(e.target.value)}
              error={fieldErrors.lastName ?? null}
            />
            <div className="flex items-center gap-2 self-end pb-2">
              <Toggle checked={enabled} onChange={setEnabled} label={t('common.accountEnabled')} />
            </div>
            {!isCreate && (
              /* Read-only indicator — no admin endpoint toggles verification
                 (re-send lives in the overflow menu). */
              <div className="flex items-center gap-2 self-end pb-2">
                <Badge tone={user?.emailVerified ? 'blue' : 'warning'}>
                  {user?.emailVerified ? t('common.verified') : t('common.unverified')}
                </Badge>
              </div>
            )}
            </form>
          </>
        ) : (
          <dl className="grid grid-cols-2 gap-4 sm:grid-cols-3">
            <DetailField label={t('common.name')}>{fullName}</DetailField>
            <DetailField label={t('common.username')}>{user?.username}</DetailField>
            <DetailField label={t('common.email')}>{user?.email}</DetailField>
            <DetailField label={t('users.accountStatus')}>
              {user && (
                <Badge tone={user.enabled ? 'green' : 'muted'}>{user.enabled ? t('common.active') : t('common.disabled')}</Badge>
              )}
            </DetailField>
            <DetailField label={t('users.verificationStatus')}>
              {user && (
                <Badge tone={user.emailVerified ? 'blue' : 'warning'}>
                  {user.emailVerified ? t('common.verified') : t('common.unverified')}
                </Badge>
              )}
            </DetailField>
            {/* [RISK-22] Brute-force lockout — lazy expiry, so only a future timestamp counts. */}
            <DetailField label={t('users.lockStatus')}>
              {user && (isLocked(user) ? (
                <span className="flex items-center gap-2">
                  <Badge tone="warning">{t('common.locked')}</Badge>
                  <span className="text-sm text-muted">
                    {t('users.lockExpiresAt', { time: formatDateTime(user.lockedUntil) })}
                  </span>
                </span>
              ) : (
                <Badge tone="muted">{t('users.notLocked')}</Badge>
              ))}
            </DetailField>
          </dl>
        )}
      </DetailPanel>

      {/* Profile is self-service only (the admin API cannot update it); skipped in create mode. */}
      {!isCreate && (
        <DetailPanel title={t('users.profileSection')}>
          <dl className="grid grid-cols-2 gap-4 sm:grid-cols-3">
            <DetailField label={t('common.phone')}>{user?.phoneNumber}</DetailField>
            <DetailField label={t('common.city')}>{user?.city}</DetailField>
            <DetailField label={t('common.country')}>{user?.country}</DetailField>
            <DetailField label={t('common.address')}>{user?.address}</DetailField>
            <DetailField label={t('common.zip')}>{user?.zipCode}</DetailField>
          </dl>
        </DetailPanel>
      )}

      {/* Temporal summary (audit stamps + login history) — read-only, fetched in parallel. */}
      {!isCreate && (
        <DetailPanel title={t('users.activitySection')}>
          <dl className="grid grid-cols-2 gap-4 sm:grid-cols-3">
            <DetailField label={t('users.createdAt')}>{formatDateTime(activity?.createdDate ?? null)}</DetailField>
            <DetailField label={t('users.createdBy')}>{activity?.createdBy ?? '—'}</DetailField>
            <DetailField label={t('users.lastLogin')}>{formatDateTime(activity?.lastLoginAt ?? null)}</DetailField>
            <DetailField label={t('users.lastFailedLogin')}>{formatDateTime(activity?.lastFailedLoginAt ?? null)}</DetailField>
            <DetailField label={t('users.updatedAt')}>{formatDateTime(activity?.updatedAt ?? null)}</DetailField>
            <DetailField label={t('users.updatedBy')}>{activity?.updatedBy ?? '—'}</DetailField>
          </dl>
        </DetailPanel>
      )}

      {/* Pickers only appear behind the canWrite-gated edit/create — read-only viewers
          never see an editable select. */}
      {formActive ? (
        <>
          <div className="grid gap-6 lg:grid-cols-2">
            <DetailPanel title={t('users.rolesSection')}>
              <RolePicker
                isMulti
                values={roleIds}
                selectedOptions={(user?.roles ?? []).map((r) => ({ value: r.id, label: r.name }))}
                onValuesChange={setRoleIds}
                placeholder={t('users.rolesAtCreate')}
              />
            </DetailPanel>
            <DetailPanel title={t('users.groupsSection')}>
              <GroupPicker
                isMulti
                values={groupIds}
                selectedOptions={(user?.groups ?? []).map((g) => ({ value: g.id, label: g.name }))}
                onValuesChange={setGroupIds}
                placeholder={t('users.groupsAtCreate')}
              />
            </DetailPanel>
          </div>
          {/* Action footer: bottom-right of the editing surface (the whole page here). */}
          <div className="mt-4 flex items-center justify-end gap-3">
            <Button variant="primary" loading={saving} onClick={() => handleSubmit()}>
              {t('common.save')}
            </Button>
            {!isCreate && (
              <Button variant="ghost" onClick={cancelEdit} disabled={saving}>
                {t('common.cancel')}
              </Button>
            )}
          </div>
        </>
      ) : user ? (
        <div className="grid gap-6 lg:grid-cols-2">
          <DetailPanel title={t('common.roles')}>
            {user.roles.length === 0 ? (
              <p className="text-sm text-muted">{t('users.noDirectRoles')}</p>
            ) : (
              <div className="flex flex-wrap gap-1.5">
                {user.roles.map((r) => (
                  <Badge key={r.id} tone="accent">{r.name}</Badge>
                ))}
              </div>
            )}
          </DetailPanel>
          <DetailPanel title={t('common.groups')}>
            {user.groups.length === 0 ? (
              <p className="text-sm text-muted">{t('users.noGroupMembership')}</p>
            ) : (
              <div className="flex flex-wrap gap-1.5">
                {user.groups.map((g) => (
                  <Badge key={g.id} tone="blue">{g.name}</Badge>
                ))}
              </div>
            )}
          </DetailPanel>
        </div>
      ) : null}

      {!isCreate && user && (
        <>
          <ConfirmDialog
            open={deleting}
            title={t('users.deleteTitle')}
            message={t('users.deleteMsg', { email: user.email })}
            confirmText={t('common.delete')}
            danger
            loading={del.isPending}
            onConfirm={async () => {
              try {
                await del.mutateAsync(user.id);
                notify.success(t('users.deleted'));
                navigate('/users');
              } catch { /* global toast */ }
            }}
            onClose={() => setDeleting(false)}
          />

          {resetting && <ResetPasswordModal user={user} onClose={() => setResetting(false)} />}

          <ConfirmDialog
            open={unlocking}
            title={t('users.unlockTitle')}
            message={t('users.unlockMsg', { email: user.email })}
            confirmText={t('users.unlock')}
            loading={unlockUser.isPending}
            onConfirm={async () => {
              try {
                await unlockUser.mutateAsync(user.id);
                notify.success(t('users.unlocked'));
                setUnlocking(false);
              } catch {
                /* global toast */
              }
            }}
            onClose={() => setUnlocking(false)}
          />

          {showPerms && (
            <PermissionListModal
              open
              onClose={() => setShowPerms(false)}
              title={effectivePerms ? t('users.effectivePerms', { count: effectivePerms.length }) : t('users.effectivePermsLoading')}
              permissions={effectivePerms}
            />
          )}
        </>
      )}
    </Page>
  );
}
