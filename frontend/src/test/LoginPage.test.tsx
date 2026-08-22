import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { LoginPage } from '../features/auth/LoginPage';
import { useAuthStore } from '../store/authStore';
import { useTenantStore } from '../store/tenantStore';
import { useLocaleStore } from '../store/localeStore';

/**
 * Unit tests for the LoginPage form flow (K-39 first tests): field rendering,
 * tenant persistence before login, submit wiring and the post-login redirect.
 */

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/login']}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/" element={<div>HOME</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('LoginPage', () => {
  beforeEach(() => {
    window.localStorage.clear();
    useLocaleStore.setState({ locale: 'en' });
    useTenantStore.setState({ tenantId: null });
  });

  it('renders the tenant, email and password fields', () => {
    renderPage();

    expect(screen.getByLabelText(/tenant/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/email/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/password/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /sign in/i })).toBeInTheDocument();
  });

  it('persists the tenant, submits credentials and redirects on success', async () => {
    const user = userEvent.setup();
    const login = vi
      .fn<(email: string, password: string) => Promise<boolean>>()
      .mockResolvedValue(true);
    useAuthStore.setState({ login, isSubmitting: false });
    renderPage();

    await user.type(screen.getByLabelText(/tenant/i), 'acme');
    await user.type(screen.getByLabelText(/email/i), 'admin@acme.dev');
    await user.type(screen.getByLabelText(/password/i), 'secret');
    await user.click(screen.getByRole('button', { name: /sign in/i }));

    expect(login).toHaveBeenCalledWith('admin@acme.dev', 'secret');
    // The tenant is stored before the request so the X-Tenant-ID header is right.
    expect(useTenantStore.getState().tenantId).toBe('acme');
    expect(await screen.findByText('HOME')).toBeInTheDocument();
  });

  it('stays on the form when login fails', async () => {
    const user = userEvent.setup();
    const login = vi
      .fn<(email: string, password: string) => Promise<boolean>>()
      .mockResolvedValue(false);
    useAuthStore.setState({ login, isSubmitting: false });
    renderPage();

    await user.type(screen.getByLabelText(/email/i), 'admin@acme.dev');
    await user.type(screen.getByLabelText(/password/i), 'wrong');
    await user.click(screen.getByRole('button', { name: /sign in/i }));

    expect(login).toHaveBeenCalled();
    expect(screen.queryByText('HOME')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /sign in/i })).toBeInTheDocument();
  });
});
