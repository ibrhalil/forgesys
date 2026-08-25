import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { ForgotPasswordPage } from '../features/auth/ForgotPasswordPage';
import { useLocaleStore } from '../store/localeStore';

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/forgot-password" element={<ForgotPasswordPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('ForgotPasswordPage', () => {
  beforeEach(() => {
    window.localStorage.clear();
    useLocaleStore.setState({ locale: 'en' });
  });
  afterEach(() => vi.unstubAllGlobals());

  it('posts the email and renders the uniform no-enumeration info copy', async () => {
    const user = userEvent.setup();
    const fetchMock = vi.fn(async () =>
      new Response(JSON.stringify({ message: 'ok' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    );
    vi.stubGlobal('fetch', fetchMock);

    renderAt('/forgot-password');
    await user.type(screen.getByLabelText('Email'), 'user@example.com');
    await user.click(screen.getByRole('button', { name: /send reset link/i }));

    expect(await screen.findByText(/If the address is registered/i)).toBeInTheDocument();
    const call = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(String(call[0])).toBe('/api/v1/auth/forgot-password');
    expect(JSON.parse(String(call[1]?.body))).toEqual({ email: 'user@example.com' });
  });

  it('surfaces a retryable error when the request fails (429 / network) instead of hanging silently', async () => {
    const user = userEvent.setup();
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        new Response(JSON.stringify({ code: 'auth_rate_limited' }), {
          status: 429,
          headers: { 'Content-Type': 'application/json' },
        }),
      ),
    );

    renderAt('/forgot-password');
    await user.type(screen.getByLabelText('Email'), 'user@example.com');
    await user.click(screen.getByRole('button', { name: /send reset link/i }));

    // The form stays up with the error copy — the user can retry.
    expect(await screen.findByRole('alert')).toHaveTextContent(/could not be sent/i);
    expect(screen.getByRole('button', { name: /send reset link/i })).toBeInTheDocument();
  });
});
