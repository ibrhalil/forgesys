import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { VerifyEmailPage } from '../features/auth/VerifyEmailPage';
import { useLocaleStore } from '../store/localeStore';

/**
 * The email-verification landing page (optional-policy flow): auto-POSTs the token
 * from the query string on mount and renders the outcome. Locale pinned to EN for
 * stable query strings.
 */
function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/verify-email" element={<VerifyEmailPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('VerifyEmailPage', () => {
  beforeEach(() => {
    window.localStorage.clear();
    useLocaleStore.setState({ locale: 'en' });
  });
  afterEach(() => vi.unstubAllGlobals());

  it('consumes the token from the link and shows the success panel', async () => {
    const fetchMock = vi.fn(async () =>
      new Response(JSON.stringify({ message: 'ok' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    );
    vi.stubGlobal('fetch', fetchMock);

    renderAt('/verify-email?token=raw-token');

    // Localized success copy (never the backend message) + the token in the body.
    expect(await screen.findByText('Email verified')).toBeInTheDocument();
    expect(screen.getByText(/your email address is verified/i)).toBeInTheDocument();
    const call = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(String(call[0])).toBe('/api/v1/auth/verify-email');
    expect(JSON.parse(String(call[1]?.body))).toEqual({ token: 'raw-token' });
  });

  it('maps backend error codes to localized copy (expired)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        new Response(JSON.stringify({ code: 'user_token_expired' }), {
          status: 400,
          headers: { 'Content-Type': 'application/json' },
        }),
      ),
    );

    renderAt('/verify-email?token=stale');

    expect(await screen.findByText('Verification failed')).toBeInTheDocument();
    expect(screen.getByText(/has expired/i)).toBeInTheDocument();
  });

  it('shows the missing-token error without firing a request when the query param is absent', () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    renderAt('/verify-email');

    expect(screen.getByText(/No token found in the verification link/i)).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
