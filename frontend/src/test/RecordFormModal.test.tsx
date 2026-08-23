import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { RecordFormModal } from '../features/apps/components/RecordFormModal';
import type { AppDetail, AppRecord } from '../features/apps/types';
import { useLocaleStore } from '../store/localeStore';

const APP_ID = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';

const APP: AppDetail = {
  id: APP_ID,
  projectId: 'proj-1',
  projectName: 'Genel',
  name: 'Orders',
  description: null,
  icon: null,
  createdDate: '2026-08-01T10:00:00Z',
  updatedAt: '2026-08-01T10:00:00Z',
  properties: [
    { id: 'p-title', appId: APP_ID, name: 'Title', type: 'TEXT', config: null, required: true, position: 0 },
    { id: 'p-count', appId: APP_ID, name: 'Count', type: 'NUMBER', config: null, required: false, position: 1 },
  ],
  views: [],
};

const RECORD: AppRecord = {
  id: 'r-1',
  appId: APP_ID,
  values: { 'p-title': 'Old title', 'p-count': 5 },
  createdDate: '2026-08-10T09:00:00Z',
  updatedAt: '2026-08-10T09:00:00Z',
  createdBy: 'u1',
};

const EMPTY_PAGE = { data: [], meta: { page: 0, pageSize: 100, totalElements: 0, totalPages: 0, hasNext: false, hasPrevious: false } };

let calls: { method: string; url: string; body?: string }[] = [];

function renderModal(record?: AppRecord) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const onClose = vi.fn();
  const view = render(
    <QueryClientProvider client={client}>
      <RecordFormModal app={APP} record={record} onClose={onClose} />
    </QueryClientProvider>,
  );
  return { ...view, onClose };
}

describe('RecordFormModal', () => {
  beforeEach(() => {
    useLocaleStore.setState({ locale: 'en' });
    calls = [];
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = String(input);
        calls.push({ method: init?.method ?? 'GET', url, body: init?.body ? String(init.body) : undefined });
        const body = url.includes('/users') ? EMPTY_PAGE : RECORD;
        return new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } });
      }),
    );
  });
  afterEach(() => vi.unstubAllGlobals());

  it('prefills the draft from the stored record in edit mode', () => {
    renderModal(RECORD);

    expect(screen.getByRole('dialog')).toHaveTextContent('Edit record');
    expect(screen.getByLabelText('Title *')).toHaveValue('Old title');
    expect(screen.getByLabelText('Count')).toHaveValue(5);
  });

  it('PATCHes only the changed key (partial merge)', async () => {
    const user = userEvent.setup();
    renderModal(RECORD);

    const title = screen.getByLabelText('Title *');
    await user.clear(title);
    await user.type(title, 'New title');
    await user.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() => {
      const patch = calls.find((c) => c.method === 'PATCH');
      expect(patch).toBeDefined();
      expect(patch!.url).toBe(`/api/v1/apps/${APP_ID}/records/r-1`);
      expect(JSON.parse(patch!.body!)).toEqual({ values: { 'p-title': 'New title' } });
    });
  });

  it('clears an emptied optional cell with null', async () => {
    const user = userEvent.setup();
    renderModal(RECORD);

    await user.clear(screen.getByLabelText('Count'));
    await user.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() => {
      const patch = calls.find((c) => c.method === 'PATCH');
      expect(JSON.parse(patch!.body!)).toEqual({ values: { 'p-count': null } });
    });
  });

  it('blocks clearing a required property inline without sending a PATCH', async () => {
    const user = userEvent.setup();
    renderModal(RECORD);

    await user.clear(screen.getByLabelText('Title *'));
    await user.click(screen.getByRole('button', { name: 'Save' }));

    expect(await screen.findByText('This required property cannot be emptied.')).toBeInTheDocument();
    expect(calls.find((c) => c.method === 'PATCH')).toBeUndefined();
  });

  it('sends nothing and closes when nothing changed', async () => {
    const user = userEvent.setup();
    const { onClose } = renderModal(RECORD);

    await user.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(onClose).toHaveBeenCalled());
    expect(calls.find((c) => c.method === 'PATCH')).toBeUndefined();
  });

  it('POSTs only the filled fields on create', async () => {
    const user = userEvent.setup();
    renderModal();

    expect(screen.getByRole('dialog')).toHaveTextContent('New record');
    await user.type(screen.getByLabelText('Title *'), 'First');
    await user.click(screen.getByRole('button', { name: 'Create' }));

    await waitFor(() => {
      const post = calls.find((c) => c.method === 'POST');
      expect(post).toBeDefined();
      expect(JSON.parse(post!.body!)).toEqual({ values: { 'p-title': 'First' } });
    });
  });
});
