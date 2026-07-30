import { useState } from 'react';
import { useMe, useUpdateMyProfile, useChangeMyPassword } from '../hooks/useRbac';
import { ApiError } from '../lib/api';
import { Button } from '../components/ui/Button';
import { TextField } from '../components/ui/Field';
import { Badge } from '../components/ui/Badge';

/**
 * Self-service account page. Any authenticated user can view and update their own
 * profile fields and change their password — no {@code iam:*} permission required
 * (the backend {@code /users/me/*} endpoints are authenticated-only). Email and
 * username are identity-bearing and read-only here.
 *
 * Two stacked cards (profile fields + password change) modelled on the dashboard
 * card layout. Password change revokes all sessions server-side
 * ({@code tokenInvalidBefore}); we surface that as an inline note.
 */
export function ProfilePage() {
  const { data: me, isLoading } = useMe();

  if (isLoading || !me) {
    return (
      <div className="flex items-center justify-center py-24">
        <span className="inline-block h-6 w-6 animate-spin rounded-full border-2 border-muted/40 border-t-accent" />
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-8">
      <header>
        <h1 className="m-0 text-3xl font-semibold tracking-tight text-white">Profile</h1>
        <p className="mt-1 text-sm text-muted">Manage your account details and password.</p>
      </header>

      <div className="rounded-xl border border-glass bg-surface p-2 backdrop-blur-md">
        <div className="flex flex-wrap items-center gap-3 px-4 py-3">
          <span className="font-medium text-main">{me.email}</span>
          <Badge tone="muted">@{me.username}</Badge>
          <Badge tone={me.enabled ? 'green' : 'muted'}>{me.enabled ? 'Active' : 'Disabled'}</Badge>
          {me.emailVerified && <Badge tone="blue">Verified</Badge>}
        </div>
      </div>

      <ProfileCard user={me} />
      <PasswordCard />
    </div>
  );
}

function ProfileCard({ user }: { user: NonNullable<ReturnType<typeof useMe>['data']> }) {
  const update = useUpdateMyProfile();
  const [firstName, setFirstName] = useState(user.firstName ?? '');
  const [lastName, setLastName] = useState(user.lastName ?? '');
  const [phoneNumber, setPhoneNumber] = useState(user.phoneNumber ?? '');
  const [address, setAddress] = useState(user.address ?? '');
  const [city, setCity] = useState(user.city ?? '');
  const [country, setCountry] = useState(user.country ?? '');
  const [zipCode, setZipCode] = useState(user.zipCode ?? '');
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  const submit = async () => {
    setError(null);
    setSaved(false);
    try {
      await update.mutateAsync({ firstName, lastName, phoneNumber, address, city, country, zipCode });
      setSaved(true);
    } catch (e) {
      setError(e instanceof ApiError ? e.body.message : 'Could not update profile');
    }
  };

  return (
    <section className="rounded-xl border border-glass bg-surface p-6 backdrop-blur-md">
      <h2 className="m-0 text-lg font-semibold text-white">Personal information</h2>
      <p className="mt-1 text-sm text-muted">Visible contact details tied to your account.</p>

      <div className="mt-5 grid grid-cols-1 gap-4 sm:grid-cols-2">
        <TextField label="First name" value={firstName} onChange={(e) => setFirstName(e.target.value)} />
        <TextField label="Last name" value={lastName} onChange={(e) => setLastName(e.target.value)} />
        <TextField label="Phone" value={phoneNumber} onChange={(e) => setPhoneNumber(e.target.value)} placeholder="+90 ..." />
        <TextField label="ZIP code" value={zipCode} onChange={(e) => setZipCode(e.target.value)} />
        <TextField label="Address" value={address} onChange={(e) => setAddress(e.target.value)} className="sm:col-span-2" />
        <TextField label="City" value={city} onChange={(e) => setCity(e.target.value)} />
        <TextField label="Country" value={country} onChange={(e) => setCountry(e.target.value)} />
      </div>

      <div className="mt-5 flex items-center gap-3">
        <Button variant="primary" loading={update.isPending} onClick={submit}>Save changes</Button>
        {saved && <span className="text-sm text-accent-green">Profile updated.</span>}
        {error && <span className="rounded-lg border border-danger/30 bg-danger/10 px-3 py-2 text-sm text-danger">{error}</span>}
      </div>
    </section>
  );
}

function PasswordCard() {
  const change = useChangeMyPassword();
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  const submit = async () => {
    setError(null);
    setSaved(false);
    try {
      await change.mutateAsync({ currentPassword, newPassword });
      setSaved(true);
      setCurrentPassword('');
      setNewPassword('');
    } catch (e) {
      if (e instanceof ApiError && e.body.code === 'user_password_incorrect') {
        setError('Current password is incorrect.');
      } else {
        setError(e instanceof ApiError ? e.body.message : 'Could not change password');
      }
    }
  };

  return (
    <section className="rounded-xl border border-glass bg-surface p-6 backdrop-blur-md">
      <h2 className="m-0 text-lg font-semibold text-white">Change password</h2>
      <p className="mt-1 text-sm text-muted">
        Changing your password signs you out of every device. You'll need to log in again.
      </p>

      <div className="mt-5 grid grid-cols-1 gap-4 sm:grid-cols-2">
        <TextField
          label="Current password"
          type="password"
          value={currentPassword}
          onChange={(e) => setCurrentPassword(e.target.value)}
          error={error}
          required
        />
        <TextField
          label="New password"
          type="password"
          value={newPassword}
          onChange={(e) => setNewPassword(e.target.value)}
          placeholder="Min 8 characters"
          hint="Minimum 8 characters"
          required
        />
      </div>

      <div className="mt-5 flex items-center gap-3">
        <Button variant="primary" loading={change.isPending} onClick={submit} disabled={!currentPassword || !newPassword}>
          Update password
        </Button>
        {saved && <span className="text-sm text-accent-green">Password changed — all other sessions ended.</span>}
      </div>
    </section>
  );
}
