import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ViewModal } from '../features/apps/components/ViewModal';
import type { AppProperty, AppView } from '../features/apps/types';
import { useLocaleStore } from '../store/localeStore';

const APP_ID = '55555555-5555-5555-5555-555555555555';

const PROPERTIES: AppProperty[] = [
  { id: 'p-title', appId: APP_ID, name: 'Title', type: 'TEXT', config: null, required: false, position: 0 },
  { id: 'p-status', appId: APP_ID, name: 'Status', type: 'SELECT', config: { options: ['Todo', 'Done'] }, required: false, position: 1 },
  { id: 'p-due', appId: APP_ID, name: 'Due', type: 'DATE', config: null, required: false, position: 2 },
];

let calls: { method: string; url: string; body?: string }[] = [];

function renderModal(view?: AppView) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={client}>
      <ViewModal appId={APP_ID} properties={PROPERTIES} view={view} onClose={vi.fn()} />
    </QueryClientProvider>,
  );
}

/** Open a react-select by combobox element and pick an option by name. */
async function pick(user: ReturnType<typeof userEvent.setup>, combobox: HTMLElement, option: string) {
  await user.click(combobox);
  await user.click(await screen.findByRole('option', { name: option }));
}

describe('ViewModal', () => {
  beforeEach(() => {
    useLocaleStore.setState({ locale: 'en' });
    calls = [];
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = String(input);
        calls.push({ method: init?.method ?? 'GET', url, body: init?.body ? String(init.body) : undefined });
        return new Response(JSON.stringify({ id: 'v-new' }), { status: 201, headers: { 'Content-Type': 'application/json' } });
      }),
    );
  });
  afterEach(() => vi.unstubAllGlobals());

  it('creates a TABLE view with an empty config', async () => {
    const user = userEvent.setup();
    renderModal();

    await user.type(screen.getByLabelText('Name'), 'Main');
    await user.click(screen.getByRole('button', { name: 'Create' }));

    const post = calls.find((c) => c.method === 'POST');
    expect(post?.url).toBe(`/api/v1/apps/${APP_ID}/views`);
    expect(JSON.parse(post!.body!)).toEqual({ name: 'Main', type: 'TABLE', config: {} });
  });

  it('requires the groupBy anchor on BOARD views and shows it inline', async () => {
    const user = userEvent.setup();
    renderModal();

    await user.type(screen.getByLabelText('Name'), 'Kanban');
    await pick(user, screen.getByRole('combobox'), 'Board');
    expect(screen.getByLabelText(/group by/i)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Create' }));

    expect(calls.find((c) => c.method === 'POST')).toBeUndefined();
    expect(screen.getByText(/pick a group-by property/i)).toBeInTheDocument();
  });

  it('creates a BOARD view with the selected groupBy property', async () => {
    const user = userEvent.setup();
    renderModal();

    await user.type(screen.getByLabelText('Name'), 'Kanban');
    await pick(user, screen.getByRole('combobox'), 'Board');
    // The groupBy select mounts after the type switch — re-query the comboboxes.
    await pick(user, (await screen.findAllByRole('combobox'))[1], 'Status');
    await user.click(screen.getByRole('button', { name: 'Create' }));

    const post = calls.find((c) => c.method === 'POST');
    expect(JSON.parse(post!.body!)).toEqual({
      name: 'Kanban',
      type: 'BOARD',
      config: { groupBy: 'p-status' },
    });
  });

  it('edits while preserving filters/sorts and resending the position', async () => {
    const user = userEvent.setup();
    const view: AppView = {
      id: 'v-1',
      appId: APP_ID,
      name: 'Board',
      type: 'BOARD',
      config: {
        groupBy: 'p-status',
        filters: [{ propertyId: 'p-title', operator: 'CONTAINS', value: 'x' }],
        sorts: [{ propertyId: 'createdAt', direction: 'desc' }],
      },
      position: 2,
    };
    renderModal(view);

    await user.clear(screen.getByLabelText('Name'));
    await user.type(screen.getByLabelText('Name'), 'Renamed');
    await user.click(screen.getByRole('button', { name: 'Save' }));

    const put = calls.find((c) => c.method === 'PUT');
    expect(put?.url).toBe(`/api/v1/apps/${APP_ID}/views/v-1`);
    expect(JSON.parse(put!.body!)).toEqual({
      name: 'Renamed',
      type: 'BOARD',
      config: {
        groupBy: 'p-status',
        filters: [{ propertyId: 'p-title', operator: 'CONTAINS', value: 'x' }],
        sorts: [{ propertyId: 'createdAt', direction: 'desc' }],
      },
      position: 2,
    });
  });
});
