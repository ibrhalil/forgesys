import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AllSessionsPage } from '../features/sessions/AllSessionsPage';
import { useLocaleStore } from '../store/localeStore';

const ALL_SESSIONS = [
  {
    sessionId: 'as-1',
    userId: 'u-1',
    email: 'alice@acme.dev',
    userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    ipAddress: '192.168.1.100',
    loginAt: '2026-08-21T09:00:00Z',
    lastSeen: '2026-08-21T12:00:00Z',
  },
  {
    sessionId: 'as-2',
    userId: 'u-2',
    email: 'bob@acme.dev',
    userAgent: 'Mozilla/5.0 (X11; Linux x86_64; rv:120.0) Gecko/20100101 Firefox/120.0',
    ipAddress: '10.0.0.20',
    loginAt: '2026-08-20T09:00:00Z',
    lastSeen: '2026-08-20T12:00:00Z',
  },
];

let calls: { url: string; method: string }[];

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <AllSessionsPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('AllSessionsPage', () => {
  beforeEach(() => {
    useLocaleStore.setState({ locale: 'en' });
    calls = [];
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = String(input);
        calls.push({ url, method: init?.method ?? 'GET' });
        const body = url === '/api/v1/sessions' ? ALL_SESSIONS : [];
        return new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } });
      }),
    );
  });
  afterEach(() => vi.unstubAllGlobals());

  it('renders all tenant sessions in a DataTable with user links', async () => {
    renderPage();

    expect(await screen.findByText('alice@acme.dev')).toBeInTheDocument();
    expect(screen.getByText('bob@acme.dev')).toBeInTheDocument();
    expect(screen.getByText('Chrome · Windows')).toBeInTheDocument();
    expect(screen.getByText('Firefox · Linux')).toBeInTheDocument();
    expect(screen.getByText('192.168.1.100')).toBeInTheDocument();
    expect(screen.getByText('10.0.0.20')).toBeInTheDocument();

    const aliceLink = screen.getByRole('link', { name: 'alice@acme.dev' });
    expect(aliceLink).toHaveAttribute('href', '/users/u-1');
  });

  it('revokes a user session via per-user admin endpoint through confirmation dialog', async () => {
    const user = userEvent.setup();
    renderPage();

    await screen.findByText('alice@acme.dev');
    const revokeButtons = screen.getAllByRole('button', { name: 'Revoke' });
    expect(revokeButtons.length).toBe(2);

    await user.click(revokeButtons[0]);

    const dialog = await screen.findByRole('dialog', { name: 'Revoke session' });
    expect(dialog).toBeInTheDocument();
    expect(screen.getByText(/This will end the session on that device/i)).toBeInTheDocument();

    const confirmBtn = within(dialog).getByRole('button', { name: 'Revoke' });
    await user.click(confirmBtn);

    await waitFor(() => {
      expect(calls.some((c) => c.method === 'DELETE' && c.url === '/api/v1/users/u-1/sessions/as-1')).toBe(true);
    });
  });
});
