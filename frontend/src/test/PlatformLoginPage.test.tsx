import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { PlatformLoginPage } from '../features/platform/PlatformLoginPage';
import { usePlatformAuthStore } from '../store/platformAuthStore';
import { useLocaleStore } from '../store/localeStore';

/**
 * K-50 platform login: the platform console's own sign-in — no tenant field
 * (platform identities are tenant-less), submit wires the platform store and
 * redirects into the console.
 */

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/platform/login']}>
      <Routes>
        <Route path="/platform/login" element={<PlatformLoginPage />} />
        <Route path="/platform" element={<div>CONSOLE</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('PlatformLoginPage', () => {
  beforeEach(() => {
    window.localStorage.clear();
    useLocaleStore.setState({ locale: 'en' });
    usePlatformAuthStore.setState({ isSubmitting: false, user: null, isAuthenticated: false });
  });

  it('renders email and password fields and NO tenant field', () => {
    renderPage();

    expect(screen.getByLabelText(/email/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/password/i)).toBeInTheDocument();
    expect(screen.queryByLabelText(/tenant/i)).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /sign in/i })).toBeInTheDocument();
  });

  it('submits credentials to the platform store and redirects on success', async () => {
    const user = userEvent.setup();
    const login = vi
      .fn<(email: string, password: string) => Promise<boolean>>()
      .mockResolvedValue(true);
    usePlatformAuthStore.setState({ login });

    renderPage();
    await user.type(screen.getByLabelText(/email/i), 'root@platform.dev');
    await user.type(screen.getByLabelText(/password/i), 'secret');
    await user.click(screen.getByRole('button', { name: /sign in/i }));

    expect(login).toHaveBeenCalledWith('root@platform.dev', 'secret');
    expect(await screen.findByText('CONSOLE')).toBeInTheDocument();
  });

  it('stays on the form when login fails', async () => {
    const user = userEvent.setup();
    const login = vi
      .fn<(email: string, password: string) => Promise<boolean>>()
      .mockResolvedValue(false);
    usePlatformAuthStore.setState({ login });

    renderPage();
    await user.type(screen.getByLabelText(/email/i), 'root@platform.dev');
    await user.type(screen.getByLabelText(/password/i), 'wrong');
    await user.click(screen.getByRole('button', { name: /sign in/i }));

    expect(login).toHaveBeenCalled();
    expect(screen.queryByText('CONSOLE')).not.toBeInTheDocument();
  });
});
