import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { RequestLogsPage } from '../features/audit/RequestLogsPage';
import { useAuthStore } from '../store/authStore';
import { useLocaleStore } from '../store/localeStore';

/** GET /api/v1/request-logs PageResponse payload. */
const REQUEST_LOGS_PAYLOAD = {
  data: [
    {
      id: 'rl-1',
      traceId: 'a1b2c3d4-1111-2222-3333-444444444444',
      method: 'GET',
      path: '/api/v1/users',
      status: 200,
      durationMs: 120,
      userId: '12345678-90ab-cdef-1234-567890abcdef',
      username: 'admin@tenant.test',
      ipAddress: '127.0.0.1',
      userAgent: 'vitest',
      requestBody: null,
      createdAt: '2026-08-23T09:00:00Z',
    },
    {
      id: 'rl-2',
      traceId: 'b2c3d4e5-1111-2222-3333-444444444444',
      method: 'POST',
      path: '/api/v1/auth/login',
      status: 401,
      durationMs: 340,
      userId: null,
      username: null,
      ipAddress: '10.0.0.5',
      userAgent: 'vitest',
      requestBody: null,
      createdAt: '2026-08-23T09:05:00Z',
    },
  ],
  meta: { page: 0, pageSize: 10, totalElements: 2, totalPages: 1, hasNext: false, hasPrevious: false },
};

let urls: string[] = [];

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <RequestLogsPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('RequestLogsPage', () => {
  beforeEach(() => {
    useLocaleStore.setState({ locale: 'en' });
    useAuthStore.setState({ hasAuthority: () => true });
    urls = [];
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = String(input);
        urls.push(url);
        return new Response(JSON.stringify(REQUEST_LOGS_PAYLOAD), { status: 200, headers: { 'Content-Type': 'application/json' } });
      }),
    );
  });
  afterEach(() => vi.unstubAllGlobals());

  it('renders the request log rows from the API, defaulting to the createdDate sort', async () => {
    renderPage();

    expect(screen.getByRole('heading', { name: 'Request Logs' })).toBeInTheDocument();
    expect(await screen.findByText('/api/v1/users')).toBeInTheDocument();
    expect(screen.getByText('admin@tenant.test')).toBeInTheDocument();
    expect(screen.getByText('340 ms')).toBeInTheDocument();

    // Regression lock: the sort param must be the entity attribute (SortGuard whitelist),
    // not the DTO wire name createdAt.
    await waitFor(() => {
      expect(urls.some((u) => u.includes('sort=createdDate%2Cdesc'))).toBe(true);
      expect(urls.some((u) => u.includes('sort=createdAt'))).toBe(false);
    });
  });

  it('re-queries with the whitelisted backend field when sorting by a column', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('/api/v1/users');

    await user.click(screen.getByRole('button', { name: 'Path' }));

    // toggleSort switches to the new field ascending and resets the page.
    await waitFor(() => {
      expect(urls.some((u) => u.includes('sort=path%2Casc'))).toBe(true);
    });
  });
});
