import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { PropertyModal } from '../features/apps/components/PropertyModal';
import type { AppProperty } from '../features/apps/types';
import { useLocaleStore } from '../store/localeStore';

const APP_ID = '55555555-5555-5555-5555-555555555555';

const TEXT_PROPERTY: AppProperty = {
  id: 'p-title',
  appId: APP_ID,
  name: 'Title',
  type: 'TEXT',
  config: null,
  required: false,
  position: 0,
};

const SELECT_PROPERTY: AppProperty = {
  id: 'p-status',
  appId: APP_ID,
  name: 'Status',
  type: 'SELECT',
  config: { options: ['Todo', 'Done'] },
  required: true,
  position: 1,
};

let calls: { method: string; url: string; body?: string }[] = [];

function renderModal(property?: AppProperty) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={client}>
      <PropertyModal appId={APP_ID} property={property} onClose={vi.fn()} />
    </QueryClientProvider>,
  );
}

describe('PropertyModal', () => {
  beforeEach(() => {
    useLocaleStore.setState({ locale: 'en' });
    calls = [];
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = String(input);
        calls.push({ method: init?.method ?? 'GET', url, body: init?.body ? String(init.body) : undefined });
        return new Response(JSON.stringify({ id: 'p-x' }), {
          status: init?.method === 'PUT' ? 200 : 201,
          headers: { 'Content-Type': 'application/json' },
        });
      }),
    );
  });
  afterEach(() => vi.unstubAllGlobals());

  it('edits a TEXT property via PUT and re-sends the type (backend @NotNull contract)', async () => {
    const user = userEvent.setup();
    renderModal(TEXT_PROPERTY);

    // The type select is frozen in edit mode but its value must still ship.
    await user.clear(screen.getByLabelText('Name'));
    await user.type(screen.getByLabelText('Name'), 'Headline');
    await user.click(screen.getByRole('button', { name: 'Save' }));

    const put = calls.find((c) => c.method === 'PUT');
    expect(put?.url).toBe(`/api/v1/apps/${APP_ID}/properties/p-title`);
    expect(JSON.parse(put!.body!)).toEqual({ name: 'Headline', type: 'TEXT', required: false, position: 0 });
  });

  it('edits a SELECT property and ships the updated options with the type', async () => {
    const user = userEvent.setup();
    renderModal(SELECT_PROPERTY);

    // Add an option via the creatable multi-select.
    const optionsCombo = screen.getByRole('combobox', { name: /options/i });
    await user.click(optionsCombo);
    await user.type(optionsCombo, 'Blocked{enter}');
    await user.click(screen.getByRole('button', { name: 'Save' }));

    const put = calls.find((c) => c.method === 'PUT');
    expect(JSON.parse(put!.body!)).toEqual({
      name: 'Status',
      type: 'SELECT',
      config: { options: ['Todo', 'Done', 'Blocked'] },
      required: true,
      position: 1,
    });
  });

  it('creates a property via POST with the chosen type (no position — auto max+1)', async () => {
    const user = userEvent.setup();
    renderModal();

    await user.type(screen.getByLabelText('Name'), 'Estimate');
    // Create mode has no position field — the backend appends at the end.
    expect(screen.queryByLabelText(/position/i)).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Create' }));

    const post = calls.find((c) => c.method === 'POST');
    expect(post?.url).toBe(`/api/v1/apps/${APP_ID}/properties`);
    expect(JSON.parse(post!.body!)).toEqual({ name: 'Estimate', type: 'TEXT', required: false });
  });

  it('re-targeting a RELATION property shows the newly picked app, not the stale one', async () => {
    const user = userEvent.setup();
    const OLD_TARGET = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb';
    const NEW_TARGET = 'cccccccc-cccc-cccc-cccc-cccccccccccc';
    const relProperty: AppProperty = {
      id: 'p-rel',
      appId: APP_ID,
      name: 'Customer',
      type: 'RELATION',
      config: { targetAppId: OLD_TARGET },
      required: false,
      position: 2,
    };
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = String(input);
        calls.push({ method: init?.method ?? 'GET', url, body: init?.body ? String(init.body) : undefined });
        // Detail seed for the ORIGINAL target + app-directory typeahead for the picker.
        const payload = url === `/api/v1/apps/${OLD_TARGET}` ? { id: OLD_TARGET, name: 'Old App' }
          : url === `/api/v1/apps/${NEW_TARGET}` ? { id: NEW_TARGET, name: 'New App' }
          : url.includes('/api/v1/apps?') ? {
              data: [
                { id: NEW_TARGET, name: 'New App', description: null, icon: null, projectId: 'proj-1', projectName: 'Genel', createdDate: '2026-08-01T10:00:00Z', updatedAt: '2026-08-01T10:00:00Z' },
                { id: 'dddddddd-dddd-dddd-dddd-dddddddddddd', name: 'Other App', description: null, icon: null, projectId: 'proj-1', projectName: 'Genel', createdDate: '2026-08-01T10:00:00Z', updatedAt: '2026-08-01T10:00:00Z' },
              ],
              meta: { page: 0, pageSize: 20, totalElements: 2, totalPages: 1, hasNext: false, hasPrevious: false },
            }
          : { id: 'p-x' };
        return new Response(JSON.stringify(payload), { status: 200, headers: { 'Content-Type': 'application/json' } });
      }),
    );

    renderModal(relProperty);

    // Edit mode seeds the current target's name.
    expect(await screen.findByText('Old App')).toBeInTheDocument();

    // Pick a different target app through the typeahead.
    const combo = screen.getByRole('combobox', { name: /target app/i });
    await user.click(combo);
    await user.type(combo, 'New');
    await user.click(await screen.findByRole('option', { name: 'New App' }));

    // The control must show the freshly picked label, not the stale seed.
    expect(screen.getByText('New App')).toBeInTheDocument();
    expect(screen.queryByText('Old App')).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Save' }));
    const put = calls.find((c) => c.method === 'PUT');
    expect(put?.url).toBe(`/api/v1/apps/${APP_ID}/properties/p-rel`);
    expect(JSON.parse(put!.body!)).toEqual({
      name: 'Customer',
      type: 'RELATION',
      config: { targetAppId: NEW_TARGET },
      required: false,
      position: 2,
    });
  });
});
