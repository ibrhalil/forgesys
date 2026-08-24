import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { UserSessionsPage } from '../features/sessions/UserSessionsPage';
import { useAuthStore } from '../store/authStore';
import { useLocaleStore } from '../store/localeStore';

const SESSIONS = [
  { sessionId: 's-1', userAgent: 'Firefox', ipAddress: '10.0.0.1', loginAt: '2026-08-20T09:00:00Z', lastSeen: '2026-08-20T12:00:00Z', current: false },
  { sessionId: 's-2', userAgent: 'Safari', ipAddress: '10.0.0.2', loginAt: '2026-08-21T09:00:00Z', lastSeen: '2026-08-21T12:00:00Z', current: false },
];

const USER = { id: 'u-1', email: 'jane@acme.dev' };

let calls: { url: string; method: string }[];

function renderAt(path: string) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/admin/users/:userId/sessions" element={<UserSessionsPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('UserSessionsPage (revoke-all overflow)', () => {
  beforeEach(() => {
    useLocaleStore.setState({ locale: 'en' });
    useAuthStore.setState({ hasAuthority: () => true });
    calls = [];
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = String(input);
        calls.push({ url, method: init?.method ?? 'GET' });
        const body = url === '/api/v1/users/u-1/sessions'
          ? SESSIONS
          : url === '/api/v1/users/u-1'
            ? USER
            : [];
        return new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } });
      }),
    );
  });
  afterEach(() => vi.unstubAllGlobals());

  it('keeps revoke-all inside the overflow menu and revokes through the confirm dialog', async () => {
    const user = userEvent.setup();
    renderAt('/admin/users/u-1/sessions');

    // Sessions render; no top-level destructive button in the page head.
    await screen.findByText('Firefox');
    expect(screen.queryByRole('button', { name: 'Revoke all' })).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Actions' }));
    await user.click(await screen.findByRole('menuitem', { name: 'Revoke all' }));

    const dialog = await screen.findByRole('dialog', { name: 'Revoke all sessions' });
    expect(dialog).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Revoke all' }));

    await waitFor(() => {
      expect(calls.some((c) => c.method === 'DELETE' && c.url === '/api/v1/users/u-1/sessions')).toBe(true);
    });
  });
});
