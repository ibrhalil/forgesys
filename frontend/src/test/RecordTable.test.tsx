import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { RecordTable } from '../features/apps/components/RecordTable';
import type { AppDetail } from '../features/apps/types';
import { useAuthStore } from '../store/authStore';
import { useLocaleStore } from '../store/localeStore';

const APP_ID = '22222222-2222-2222-2222-222222222222';
const USER_ID = '12345678-90ab-cdef-1234-567890abcdef';

const APP: AppDetail = {
  id: APP_ID,
  name: 'Orders',
  description: null,
  icon: null,
  createdDate: '2026-08-01T10:00:00Z',
  updatedAt: '2026-08-01T10:00:00Z',
  properties: [
    { id: 'p-text', appId: APP_ID, name: 'Title', type: 'TEXT', config: null, required: false, position: 0 },
    { id: 'p-user', appId: APP_ID, name: 'Owner', type: 'USER', config: null, required: false, position: 1 },
  ],
  views: [],
};

const RECORD = {
  id: 'r-1',
  appId: APP_ID,
  values: { 'p-text': 'Old title', 'p-user': USER_ID },
  createdDate: '2026-08-10T09:00:00Z',
  updatedAt: '2026-08-10T09:00:00Z',
  createdBy: 'u1',
};

const RECORDS_PAYLOAD = {
  data: [RECORD],
  meta: { page: 0, pageSize: 10, totalElements: 1, totalPages: 1, hasNext: false, hasPrevious: false },
};

let calls: { method: string; url: string; body?: string }[] = [];

function renderTable() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <RecordTable app={APP} />
    </QueryClientProvider>,
  );
}

describe('RecordTable inline edit', () => {
  beforeEach(() => {
    useLocaleStore.setState({ locale: 'en' });
    useAuthStore.setState({ hasAuthority: () => true });
    calls = [];
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = String(input);
        calls.push({ method: init?.method ?? 'GET', url, body: init?.body ? String(init.body) : undefined });
        const body = url.includes('/records') && (init?.method ?? 'GET') === 'GET' ? RECORDS_PAYLOAD : RECORD;
        return new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } });
      }),
    );
  });
  afterEach(() => vi.unstubAllGlobals());

  it('edits a TEXT cell inline and PATCHes the parsed value', async () => {
    const user = userEvent.setup();
    renderTable();

    await user.click(await screen.findByRole('button', { name: 'Old title' }));
    const input = screen.getByRole('textbox');
    await user.clear(input);
    await user.type(input, 'New title{Enter}');

    await waitFor(() => {
      const patch = calls.find((c) => c.method === 'PATCH');
      expect(patch).toBeDefined();
      expect(patch!.url).toBe(`/api/v1/apps/${APP_ID}/records/${RECORD.id}`);
      expect(JSON.parse(patch!.body!)).toEqual({ values: { 'p-text': 'New title' } });
    });
  });

  it('cancels the edit with Escape without sending anything', async () => {
    const user = userEvent.setup();
    renderTable();

    await user.click(await screen.findByRole('button', { name: 'Old title' }));
    const input = screen.getByRole('textbox');
    await user.clear(input);
    await user.type(input, 'Discarded{Escape}');

    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
    expect(calls.find((c) => c.method === 'PATCH')).toBeUndefined();
  });

  it('renders USER cells read-only as a shortened id', async () => {
    const user = userEvent.setup();
    renderTable();

    const cell = await screen.findByText('12345678…');
    await user.click(cell);
    // No inline editor opens for picker-dependent types.
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
    expect(cell).toHaveAttribute('title', USER_ID);
  });
});
