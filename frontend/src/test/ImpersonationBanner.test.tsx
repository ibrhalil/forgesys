import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { ImpersonationBanner } from '../components/ImpersonationBanner';
import { useAuthStore } from '../store/authStore';
import { useLocaleStore } from '../store/localeStore';

/**
 * K-50 impersonation banner (tenant shell): renders only while /me reports
 * impersonation info, marks the window title, and ends the session via the
 * tenant logout on exit.
 */

const ME = {
  id: 'u-admin',
  email: 'admin@acme.dev',
  username: 'admin',
  firstName: null,
  lastName: null,
  phoneNumber: null,
  address: null,
  city: null,
  country: null,
  zipCode: null,
  enabled: true,
  emailVerified: true,
  lockedUntil: null,
  roles: [],
  groups: [],
  authorities: [],
};

function renderBanner() {
  return render(
    <MemoryRouter initialEntries={['/']}>
      <Routes>
        <Route path="/" element={<ImpersonationBanner />} />
        <Route path="/login" element={<div>LOGIN</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('ImpersonationBanner', () => {
  beforeEach(() => {
    useLocaleStore.setState({ locale: 'en' });
    document.title = 'ForgeSys';
    useAuthStore.setState({ user: null, logout: vi.fn().mockResolvedValue(undefined) });
  });

  it('renders nothing without impersonation info', () => {
    useAuthStore.setState({ user: { ...ME, impersonation: null } });
    const { container } = renderBanner();

    expect(screen.queryByTestId('impersonation-banner')).not.toBeInTheDocument();
    expect(container.firstChild).toBeNull();
    expect(document.title).toBe('ForgeSys');
  });

  it('shows the actor email, marks the title, and exits via logout', async () => {
    const user = userEvent.setup();
    const logout = vi.fn().mockResolvedValue(undefined);
    useAuthStore.setState({
      user: { ...ME, impersonation: { actorId: 'p-root', actorEmail: 'root@platform.dev' } },
      logout,
    });
    renderBanner();

    expect(screen.getByTestId('impersonation-banner')).toBeInTheDocument();
    expect(screen.getByText(/root@platform\.dev/)).toBeInTheDocument();
    expect(document.title).toBe('[imp] ForgeSys');

    await user.click(screen.getByRole('button', { name: /end session/i }));
    expect(logout).toHaveBeenCalledTimes(1);
    expect(await screen.findByText('LOGIN')).toBeInTheDocument();
  });
});
