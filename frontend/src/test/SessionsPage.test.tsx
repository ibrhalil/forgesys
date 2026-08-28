import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { SessionsPage } from '../features/sessions/SessionsPage';
import { useLocaleStore } from '../store/localeStore';

const MY_SESSIONS = [
  {
    sessionId: 'sess-1',
    userAgent: 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    ipAddress: '192.168.1.10',
    loginAt: '2026-08-21T09:00:00Z',
    lastSeen: '2026-08-21T12:00:00Z',
    current: true,
  },
  {
    sessionId: 'sess-2',
    userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:120.0) Gecko/20100101 Firefox/120.0',
    ipAddress: '10.0.0.5',
    loginAt: '2026-08-20T09:00:00Z',
    lastSeen: '2026-08-20T12:00:00Z',
    current: false,
  },
];

let calls: { url: string; method: string }[];

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <SessionsPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('SessionsPage', () => {
  beforeEach(() => {
    useLocaleStore.setState({ locale: 'en' });
    calls = [];
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = String(input);
        calls.push({ url, method: init?.method ?? 'GET' });
        const body = url === '/api/v1/users/me/sessions' ? MY_SESSIONS : [];
        return new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } });
      }),
    );
  });
  afterEach(() => vi.unstubAllGlobals());

  it('renders sessions in a DataTable with This Device badge on current session', async () => {
    renderPage();

    expect(await screen.findByText('Chrome · macOS')).toBeInTheDocument();
    expect(screen.getByText('This device')).toBeInTheDocument();
    expect(screen.getByText('Firefox · Windows')).toBeInTheDocument();
    expect(screen.getByText('192.168.1.10')).toBeInTheDocument();
    expect(screen.getByText('10.0.0.5')).toBeInTheDocument();
  });

  it('revokes current session through confirmation dialog', async () => {
    const user = userEvent.setup();
    renderPage();

    await screen.findByText('Chrome · macOS');
    const revokeButtons = screen.getAllByRole('button', { name: 'Revoke' });
    expect(revokeButtons.length).toBe(2);

    // Click revoke on first session (sess-1, current)
    await user.click(revokeButtons[0]);

    const dialog = await screen.findByRole('dialog', { name: 'Revoke session' });
    expect(dialog).toBeInTheDocument();
    expect(screen.getByText(/This will sign you out/i)).toBeInTheDocument();

    const confirmBtn = within(dialog).getByRole('button', { name: 'Revoke' });
    await user.click(confirmBtn);

    await waitFor(() => {
      expect(calls.some((c) => c.method === 'DELETE' && c.url === '/api/v1/users/me/sessions/sess-1')).toBe(true);
    });
  });
});
