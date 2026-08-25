import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { ResetPasswordPage } from '../features/auth/ResetPasswordPage';
import { useLocaleStore } from '../store/localeStore';

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/reset-password" element={<ResetPasswordPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('ResetPasswordPage', () => {
  beforeEach(() => {
    window.localStorage.clear();
    useLocaleStore.setState({ locale: 'en' });
  });
  afterEach(() => vi.unstubAllGlobals());

  it('submits token + new password and shows the done panel with sessions-signed-out copy', async () => {
    const user = userEvent.setup();
    const fetchMock = vi.fn(async () =>
      new Response(JSON.stringify({ message: 'ok' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    );
    vi.stubGlobal('fetch', fetchMock);

    renderAt('/reset-password?token=raw-token');
    await user.type(screen.getByLabelText('New password'), 'NewSecret123!');
    await user.type(screen.getByLabelText('New password (confirm)'), 'NewSecret123!');
    await user.click(screen.getByRole('button', { name: /update my password/i }));

    expect(await screen.findByText(/All sessions were signed out/i)).toBeInTheDocument();
    const call = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(String(call[0])).toBe('/api/v1/auth/reset-password');
    expect(JSON.parse(String(call[1]?.body))).toEqual({ token: 'raw-token', newPassword: 'NewSecret123!' });
  });

  it('rejects mismatched confirmation locally without a request', async () => {
    const user = userEvent.setup();
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    renderAt('/reset-password?token=raw-token');
    await user.type(screen.getByLabelText('New password'), 'NewSecret123!');
    await user.type(screen.getByLabelText('New password (confirm)'), 'Different123!');
    await user.click(screen.getByRole('button', { name: /update my password/i }));

    expect(await screen.findByText('Passwords do not match.')).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('maps the already-used backend code to localized copy', async () => {
    const user = userEvent.setup();
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        new Response(JSON.stringify({ code: 'user_token_already_used' }), {
          status: 400,
          headers: { 'Content-Type': 'application/json' },
        }),
      ),
    );

    renderAt('/reset-password?token=spent');
    await user.type(screen.getByLabelText('New password'), 'NewSecret123!');
    await user.type(screen.getByLabelText('New password (confirm)'), 'NewSecret123!');
    await user.click(screen.getByRole('button', { name: /update my password/i }));

    expect(await screen.findByText('This reset link has already been used.')).toBeInTheDocument();
  });

  it('shows the reset-specific missing-token error without a request', () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    renderAt('/reset-password');

    expect(screen.getByText(/No token found in the reset link/i)).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
