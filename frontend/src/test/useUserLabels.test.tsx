import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useUserLabels } from '../features/users/hooks';
import { useLocaleStore } from '../store/localeStore';

/** Directory page covers jane only — zed lives beyond the first page. */
const DIRECTORY_PAGE = {
  data: [
    { id: 'u-jane', email: 'jane@acme.dev', username: 'jane', emailVerified: true, firstName: null, lastName: null, enabled: true, lockedUntil: null, lastLoginAt: null, createdDate: '2026-08-01T10:00:00Z', roleCount: 0, groupCount: 0 },
  ],
  meta: { page: 0, pageSize: 100, totalElements: 200, totalPages: 2, hasNext: true, hasPrevious: false },
};

const ZED_DETAIL = {
  id: 'u-zed',
  email: 'zed@acme.dev',
  username: 'zed',
  emailVerified: true,
  firstName: null,
  lastName: null,
  enabled: true,
  lockedUntil: null,
  roles: [],
  groups: [],
};

let calls: { url: string }[];

function Host({ ids }: { ids: Array<string | null | undefined> }) {
  const labels = useUserLabels(ids);
  return (
    <ul>
      {[...labels.entries()].map(([id, email]) => (
        <li key={id}>{`${id}=${email}`}</li>
      ))}
    </ul>
  );
}

describe('useUserLabels', () => {
  beforeEach(() => {
    useLocaleStore.setState({ locale: 'en' });
    calls = [];
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = String(input);
        calls.push({ url });
        // Directory list (query string) vs detail (bare id path).
        if (url.startsWith('/api/v1/users?')) return json(DIRECTORY_PAGE);
        if (url === '/api/v1/users/u-zed') return json(ZED_DETAIL);
        return json({ code: 'resource_not_found' }, 404);
      }),
    );
  });
  afterEach(() => vi.unstubAllGlobals());

  function json(payload: unknown, status = 200) {
    return new Response(JSON.stringify(payload), { status, headers: { 'Content-Type': 'application/json' } });
  }

  it('resolves on-page ids from the directory page and off-page ids via detail fetches', async () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={client}>
        <Host ids={['u-jane', 'u-zed', null, undefined, 'u-jane']} />
      </QueryClientProvider>,
    );

    await screen.findByText('u-zed=zed@acme.dev');
    expect(screen.getByText('u-jane=jane@acme.dev')).toBeInTheDocument();

    await waitFor(() => {
      // Jane was on the warm page — never a detail request for her.
      expect(calls.some((c) => c.url === '/api/v1/users/u-jane')).toBe(false);
      // Zed missed it — exactly one detail fetch.
      expect(calls.filter((c) => c.url === '/api/v1/users/u-zed')).toHaveLength(1);
    });
  });
});
