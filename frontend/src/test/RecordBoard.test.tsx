import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { RecordBoard } from '../features/apps/components/RecordBoard';
import { cellDisplay } from '../features/apps/cellValue';
import type { AppDetail, AppRecord, AppView } from '../features/apps/types';
import { useAuthStore } from '../store/authStore';
import { useLocaleStore } from '../store/localeStore';

const APP_ID = '22222222-2222-2222-2222-222222222222';

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
    { id: 'p-title', appId: APP_ID, name: 'Title', type: 'TEXT', config: null, required: false, position: 0 },
    { id: 'p-status', appId: APP_ID, name: 'Status', type: 'SELECT', config: { options: ['Todo', 'Done'] }, required: false, position: 1 },
  ],
  views: [],
};

const VIEW: AppView = {
  id: 'v-board',
  appId: APP_ID,
  name: 'Board',
  type: 'BOARD',
  config: { groupBy: 'p-status' },
  position: 0,
};

const R1: AppRecord = { id: 'r-1', appId: APP_ID, values: { 'p-title': 'Fix login', 'p-status': 'Todo' }, createdDate: '2026-08-10T09:00:00Z', updatedAt: '2026-08-10T09:00:00Z', createdBy: 'u1' };
const R2: AppRecord = { id: 'r-2', appId: APP_ID, values: { 'p-title': 'Ship release', 'p-status': 'Done' }, createdDate: '2026-08-11T09:00:00Z', updatedAt: '2026-08-11T09:00:00Z', createdBy: 'u1' };
const R3: AppRecord = { id: 'r-3', appId: APP_ID, values: { 'p-title': 'No status' }, createdDate: '2026-08-12T09:00:00Z', updatedAt: '2026-08-12T09:00:00Z', createdBy: 'u1' };

const resolve = cellDisplay;

let calls: { method: string; url: string; body?: string }[] = [];

function renderBoard(props: Partial<Parameters<typeof RecordBoard>[0]> = {}) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <RecordBoard
        app={APP}
        view={VIEW}
        records={[R1, R2, R3]}
        isLoading={false}
        resolve={resolve}
        onRequestDelete={vi.fn()}
        onRequestEdit={vi.fn()}
        {...props}
      />
    </QueryClientProvider>,
  );
}

describe('RecordBoard', () => {
  beforeEach(() => {
    useLocaleStore.setState({ locale: 'en' });
    useAuthStore.setState({ hasAuthority: () => true });
    calls = [];
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = String(input);
        calls.push({ method: init?.method ?? 'GET', url, body: init?.body ? String(init.body) : undefined });
        return new Response(JSON.stringify(R1), { status: 200, headers: { 'Content-Type': 'application/json' } });
      }),
    );
  });
  afterEach(() => vi.unstubAllGlobals());

  it('derives columns from the groupBy options plus an empty bucket', () => {
    renderBoard();

    // 'Todo'/'Done' each appear twice: column badge + the card mover's selected
    // value of the card living in that column. 'No value' only heads the bucket.
    expect(screen.getAllByText('Todo')).toHaveLength(2);
    expect(screen.getAllByText('Done')).toHaveLength(2);
    expect(screen.getAllByText('No value')).toHaveLength(1);
    // Cards render.
    expect(screen.getByText('Fix login')).toBeInTheDocument();
    expect(screen.getByText('Ship release')).toBeInTheDocument();
    expect(screen.getByText('No status')).toBeInTheDocument();
  });

  it('moves a card across columns by PATCHing the groupBy value', async () => {
    const user = userEvent.setup();
    renderBoard();

    // First mover belongs to the first card (Fix login — Todo).
    const mover = screen.getAllByRole('combobox')[0];
    await user.click(mover);
    await user.click(await screen.findByRole('option', { name: 'Done' }));

    await waitFor(() => {
      const patch = calls.find((c) => c.method === 'PATCH');
      expect(patch).toBeDefined();
      expect(patch!.url).toBe(`/api/v1/apps/${APP_ID}/records/r-1`);
      expect(JSON.parse(patch!.body!)).toEqual({ values: { 'p-status': 'Done' } });
    });
  });

  it('hides the mover without apps:record:write', () => {
    useAuthStore.setState({ hasAuthority: (a: string) => a !== 'apps:record:write' });
    renderBoard();

    expect(screen.queryByRole('combobox')).not.toBeInTheDocument();
    // Cards still render read-only.
    expect(screen.getByText('Fix login')).toBeInTheDocument();
  });

  it('opens the record form through the card overflow menu edit action', async () => {
    const user = userEvent.setup();
    const onEdit = vi.fn();
    renderBoard({ onRequestEdit: onEdit });

    // Edit lives inside the card's overflow menu (RecordGallery pattern), not as a button.
    expect(screen.queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument();
    await user.click(screen.getAllByRole('button', { name: 'Actions' })[0]);
    await user.click(await screen.findByRole('menuitem', { name: 'Edit' }));
    expect(onEdit).toHaveBeenCalledWith(R1);
  });

  it('falls back to an empty state when the groupBy property is gone', () => {
    renderBoard({ view: { ...VIEW, config: { groupBy: 'p-missing' } } });
    expect(screen.getByText(/group-by property is missing/i)).toBeInTheDocument();
  });
});
