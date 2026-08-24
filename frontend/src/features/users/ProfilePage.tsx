import { useState } from 'react';
import { useUpdateMyProfile, useChangeMyPassword } from './hooks';
import { useAuthStore } from '../../store/authStore';
import type { MeResponse } from '../auth/types';
import { notify, extractFieldErrors } from '../../lib/notify';
import { Page } from '../../components/Page';
import { DetailPanel } from '../../components/detail/DetailPanel';
import { Button } from '../../components/ui/Button';
import { TextField } from '../../components/ui/Field';
import { Spinner } from '../../components/ui/Spinner';
import { Badge } from '../../components/ui/Badge';
import { useT } from '../../lib/i18n';

/**
 * Self-service account page. Any authenticated user can view and update their own
 * profile fields and change their password — no {@code iam:*} permission required
 * (the backend {@code /users/me/*} endpoints are authenticated-only). Email and
 * username are identity-bearing and read-only here.
 *
 * Identity summary strip over two DetailPanels (profile fields + password change).
 * Password change revokes all sessions server-side ({@code tokenInvalidBefore});
 * we surface that as an inline note.
 */
export function ProfilePage() {
  const { t } = useT();
  // /users/me is the single self endpoint (K-37) — the session store owns the snapshot.
  const me = useAuthStore((s) => s.user);
  const isLoading = useAuthStore((s) => s.isLoading);

  if (isLoading || !me) {
    return (
      <div className="flex items-center justify-center py-16">
        <Spinner className="border-muted/40 border-t-accent" />
      </div>
    );
  }

  return (
    <Page
      breadcrumb={[{ label: t('profile.title') }]}
      title={t('profile.title')}
      description={t('profile.desc')}
    >

      <div className="rounded-xl border border-glass bg-surface p-5 backdrop-blur-md">
        <div className="flex flex-wrap items-center gap-3 py-0">
          <span className="font-medium text-main">{me.email}</span>
          <Badge tone="muted">@{me.username}</Badge>
          <Badge tone={me.enabled ? 'green' : 'muted'}>{me.enabled ? t('common.active') : t('common.disabled')}</Badge>
          {me.emailVerified && <Badge tone="blue">{t('common.verified')}</Badge>}
        </div>
      </div>

      <ProfileCard user={me} />
      <PasswordCard />
    </Page>
  );
}

function ProfileCard({ user }: { user: MeResponse }) {
  const { t } = useT();
  const update = useUpdateMyProfile();
  const [firstName, setFirstName] = useState(user.firstName ?? '');
  const [lastName, setLastName] = useState(user.lastName ?? '');
  const [phoneNumber, setPhoneNumber] = useState(user.phoneNumber ?? '');
  const [address, setAddress] = useState(user.address ?? '');
  const [city, setCity] = useState(user.city ?? '');
  const [country, setCountry] = useState(user.country ?? '');
  const [zipCode, setZipCode] = useState(user.zipCode ?? '');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const submit = async () => {
    setFieldErrors({});
    try {
      await update.mutateAsync({ firstName, lastName, phoneNumber, address, city, country, zipCode });
      notify.success(t('profile.updated'));
    } catch (e) {
      setFieldErrors(extractFieldErrors(e));
    }
  };

  return (
    <DetailPanel title={t('profile.personalTitle')}>
      <p className="m-0 mb-4 text-sm text-muted">{t('profile.personalDesc')}</p>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <TextField label={t('common.firstName')} value={firstName} onChange={(e) => setFirstName(e.target.value)} error={fieldErrors.firstName ?? null} />
        <TextField label={t('common.lastName')} value={lastName} onChange={(e) => setLastName(e.target.value)} error={fieldErrors.lastName ?? null} />
        <TextField label={t('common.phone')} value={phoneNumber} onChange={(e) => setPhoneNumber(e.target.value)} placeholder="+90 ..." error={fieldErrors.phoneNumber ?? null} />
        <TextField label={t('common.zip')} value={zipCode} onChange={(e) => setZipCode(e.target.value)} error={fieldErrors.zipCode ?? null} />
        <TextField label={t('common.address')} value={address} onChange={(e) => setAddress(e.target.value)} className="sm:col-span-2" error={fieldErrors.address ?? null} />
        <TextField label={t('common.city')} value={city} onChange={(e) => setCity(e.target.value)} error={fieldErrors.city ?? null} />
        <TextField label={t('common.country')} value={country} onChange={(e) => setCountry(e.target.value)} error={fieldErrors.country ?? null} />
      </div>

      <div className="mt-4 flex items-center justify-end gap-3">
        <Button variant="primary" loading={update.isPending} onClick={submit}>{t('profile.saveChanges')}</Button>
      </div>
    </DetailPanel>
  );
}

function PasswordCard() {
  const { t } = useT();
  const change = useChangeMyPassword();
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const submit = async () => {
    setFieldErrors({});
    try {
      await change.mutateAsync({ currentPassword, newPassword });
      notify.success(t('profile.pwdChanged'));
      setCurrentPassword('');
      setNewPassword('');
    } catch (e) {
      setFieldErrors(extractFieldErrors(e));
    }
  };

  return (
    <DetailPanel title={t('profile.pwdTitle')}>
      <p className="m-0 mb-4 text-sm text-muted">
        {t('profile.pwdDesc')}
      </p>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <TextField
          label={t('profile.currentPassword')}
          type="password"
          value={currentPassword}
          onChange={(e) => setCurrentPassword(e.target.value)}
          error={fieldErrors.currentPassword ?? null}
          required
        />
        <TextField
          label={t('common.newPassword')}
          type="password"
          value={newPassword}
          onChange={(e) => setNewPassword(e.target.value)}
          placeholder={t('common.min8')}
          hint={t('common.min8')}
          error={fieldErrors.newPassword ?? null}
          required
        />
      </div>

      <div className="mt-4 flex items-center justify-end gap-3">
        <Button variant="primary" loading={change.isPending} onClick={submit} disabled={!currentPassword || !newPassword}>
          {t('profile.updatePassword')}
        </Button>
      </div>
    </DetailPanel>
  );
}
