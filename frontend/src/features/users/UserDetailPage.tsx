import { useState, type FormEvent } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { LuEllipsisVertical, LuKeyRound, LuListChecks, LuMonitor, LuPencil, LuTrash2, LuLockOpen } from 'react-icons/lu';
import {
  useUser, useUserEffectivePermissions, useUserActivity, useCreateUser, useUpdateUser,
  useSetUserRoles, useSetUserGroups, useDeleteUser, useUnlockUser,
} from './hooks';
import { isLocked } from './types';
import { formatDateTime } from '../../lib/format';
import { useRoles } from '../roles/hooks';
import { useGroups } from '../groups/hooks';
import { useAuthStore } from '../../store/authStore';
import { notify, extractFieldErrors } from '../../lib/notify';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { TextField } from '../../components/ui/Field';
import { SelectInput } from '../../components/ui/SelectInput';
import { RowMenu } from '../../components/ui/RowMenu';
import { Toggle } from '../../components/ui/Toggle';
import { toOptions, type SelectOption } from '../../lib/select';
import { DetailPanel, DetailField } from '../../components/detail/DetailPanel';
import { Page } from '../../components/Page';
import { PermissionListModal } from '../../components/detail/PermissionListModal';
import { DetailLoading, DetailNotFound } from '../../components/detail/DetailFallback';
import { ResetPasswordModal } from './components/ResetPasswordModal';
import { PERMISSIONS } from '../../lib/permissions';
import { useT } from '../../lib/i18n';

/**
 * The single user screen: view, edit and create in one page.
 *
 * - `/users/new` — create mode: full form (identity + initial roles/groups), on
 *   success navigates to the created user's view mode.
 * - `/users/:userId` — view mode: read-only panels (identity, profile, role/group
 *   chips). The Edit button puts the WHOLE page into edit mode — identity form plus
 *   role/group pickers under one Save/Cancel (email/username stay read-only — the
 *   backend only updates first/last name + enabled). This mirrors create exactly:
 *   one editing surface, one footer.
 *
 * Edit-mode save is diff-based and sequential (NOT atomic): only the changed parts
 * are sent (identity update / role set / group set), so unchanged assignments never
 * trigger redundant session revocations. A mid-sequence failure keeps edit mode
 * with the drafts intact; re-saving is idempotent (the already-sent part no longer
 * shows as dirty against the refetched user).
 *
 * Drafts are populated once, on entering edit mode (startEdit) — NOT from a `user`
 * effect — so a background refetch (a save invalidates ['users']) can never clobber
 * in-progress edits.
 */
export function UserDetailPage() {
  const { t } = useT();
  const navigate = useNavigate();
  const { userId } = useParams<{ userId: string }>();
  const isCreate = !userId;

  const { data: user, isLoading } = useUser(isCreate ? undefined : userId);
  const { data: effectivePerms } = useUserEffectivePermissions(isCreate ? undefined : userId);
  const { data: activity } = useUserActivity(isCreate ? undefined : userId);
  const { data: rolesData } = useRoles({ size: 200, sort: 'name' });
  const { data: groupsData } = useGroups({ size: 200, sort: 'name' });

  const create = useCreateUser();
  const update = useUpdateUser();
  const setRoles = useSetUserRoles();
  const setGroups = useSetUserGroups();
  const del = useDeleteUser();
  const unlockUser = useUnlockUser();
  const currentUserId = useAuthStore((s) => s.user?.id);
  const canWrite = useAuthStore((s) => s.hasAuthority(PERMISSIONS.USER_WRITE));
  const canDelete = useAuthStore((s) => s.hasAuthority(PERMISSIONS.USER_DELETE));

  const [editing, setEditing] = useState(false);
  const [resetting, setResetting] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [unlocking, setUnlocking] = useState(false);
  const [showPerms, setShowPerms] = useState(false);

  // Form state (create + edit)
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [username, setUsername] = useState('');
  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [enabled, setEnabled] = useState(true);
  const [roleIds, setRoleIds] = useState<string[]>([]);
  const [groupIds, setGroupIds] = useState<string[]>([]);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const roleOptions = toOptions(rolesData?.items ?? [], (r) => r.id, (r) => r.name);
  const groupOptions = toOptions(groupsData?.items ?? [], (g) => g.id, (g) => g.name);

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
    // Restore every draft from the loaded user.
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
    // Client-side checks first — no backend round-trip for obviously invalid input.
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
      // Unified edit: diff each part against the persisted user and send only what
      // changed (sequential, not atomic — see the component docblock).
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
          {/* Head pattern: at most one visible action + overflow menu (RowMenu).
              Same authority gates as the list row menu — the backend enforces the
              real security; an empty items array renders no trigger. */}
          {canWrite && !editing && (
            <Button size="sm" variant="ghost" onClick={startEdit}>
              <LuPencil className="h-3.5 w-3.5" />
              {t('common.edit')}
            </Button>
          )}
          <RowMenu
            ariaLabel={t('common.actions')}
            icon={LuEllipsisVertical}
            items={[
              ...(canWrite
                ? [
                    { label: t('users.passwordBtn'), onClick: () => setResetting(true), icon: LuKeyRound },
                    { label: t('nav.sessions'), onClick: () => navigate(`/admin/users/${user.id}/sessions`), icon: LuMonitor },
                  ]
                : []),
              // Unlock only makes sense while an active lock window is running.
              ...(canWrite && isLocked(user)
                ? [{ label: t('users.unlock'), onClick: () => setUnlocking(true), icon: LuLockOpen }]
                : []),
              // Effective permissions live behind a searchable modal — the page itself
              // is iam:user:read gated, so every viewer may open it.
              { label: t('users.viewEffectivePerms'), onClick: () => setShowPerms(true), icon: LuListChecks },
              /* Self-delete is rejected by the backend (409 self_delete_forbidden) — omit it on the actor's own page. */
              ...(canDelete && user.id !== currentUserId
                ? [{ label: t('common.delete'), onClick: () => setDeleting(true), icon: LuTrash2, danger: true }]
                : []),
            ]}
          />
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
                {/* Identity-bearing fields are immutable by contract — shown as disabled
                    inputs (bound straight to the persisted user, no draft state). */}
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
              /* Read-only status indicator — the backend has no admin endpoint to
                 toggle email verification (re-send lives in the overflow menu). */
              <div className="flex items-center gap-2 self-end pb-2">
                <Toggle checked={!!user?.emailVerified} onChange={() => {}} label={t('users.emailVerified')} disabled />
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

      {/* Profile fields are self-service only (the admin API cannot update them) —
          read-only here. Meaningless before the user exists, so create mode skips it. */}
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

      {/* Account activity: temporal summary (audit stamps + login history), read-only
          by nature — fetched in parallel, never part of the edit surface. */}
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

      {/* Roles / groups: pickers while editing (create saves them with the
          CreateUserRequest; edit saves them in the unified diff-based submit),
          read-only name chips in view mode — pickers only ever appear behind the
          canWrite-gated Edit/Create, so read-only viewers never see an editable
          select. */}
      {formActive ? (
        <>
          <div className="grid gap-6 lg:grid-cols-2">
            <DetailPanel title={t('users.rolesSection')}>
              <SelectInput
                isMulti
                isClearable
                options={roleOptions}
                value={roleOptions.filter((o) => roleIds.includes(o.value))}
                onChange={(next) => setRoleIds(((next as SelectOption[]) ?? []).map((o) => o.value))}
                placeholder={t('users.rolesAtCreate')}
              />
            </DetailPanel>
            <DetailPanel title={t('users.groupsSection')}>
              <SelectInput
                isMulti
                isClearable
                options={groupOptions}
                value={groupOptions.filter((o) => groupIds.includes(o.value))}
                onChange={(next) => setGroupIds(((next as SelectOption[]) ?? []).map((o) => o.value))}
                placeholder={t('users.groupsAtCreate')}
              />
            </DetailPanel>
          </div>
          {/* Standard action footer: bottom-right of the editing surface (the whole
              page is the editing surface in unified mode). */}
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
