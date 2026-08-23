import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AppFormModal } from '../features/apps/components/AppFormModal';
import type { App } from '../features/apps/types';
import { useLocaleStore } from '../store/localeStore';

const APP: App = {
  id: 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
  projectId: 'proj-1',
  projectName: 'Genel',
  name: 'Order Tracking',
  description: 'desc',
  icon: '📦',
  createdDate: '2026-08-01T10:00:00Z',
  updatedAt: '2026-08-01T10:00:00Z',
};

let calls: { method: string; url: string; body?: string }[] = [];

function renderModal(app?: App) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <AppFormModal app={app} onClose={vi.fn()} />
    </QueryClientProvider>,
  );
}

describe('AppFormModal icon picker', () => {
  beforeEach(() => {
    useLocaleStore.setState({ locale: 'en' });
    calls = [];
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = String(input);
        calls.push({ method: init?.method ?? 'GET', url, body: init?.body ? String(init.body) : undefined });
        return new Response(JSON.stringify(APP), { status: 200, headers: { 'Content-Type': 'application/json' } });
      }),
    );
  });
  afterEach(() => vi.unstubAllGlobals());

  it('sends the selected emoji on create and omits it when none', async () => {
    const user = userEvent.setup();
    renderModal();

    await user.type(screen.getByLabelText('Name'), 'Inventory');
    await user.click(screen.getByRole('button', { name: '🧾' }));
    await user.click(screen.getByRole('button', { name: 'Create' }));

    await waitFor(() => {
      const post = calls.find((c) => c.method === 'POST');
      expect(post).toBeDefined();
      expect(JSON.parse(post!.body!)).toEqual({ name: 'Inventory', icon: '🧾' });
    });
  });

  it('creates without an icon when none is picked', async () => {
    const user = userEvent.setup();
    renderModal();

    await user.type(screen.getByLabelText('Name'), 'Bare');
    await user.click(screen.getByRole('button', { name: 'Create' }));

    await waitFor(() => {
      const post = calls.find((c) => c.method === 'POST');
      expect(JSON.parse(post!.body!)).toEqual({ name: 'Bare' });
    });
  });

  it('preselects the stored icon on edit and sends an explicit null when cleared', async () => {
    const user = userEvent.setup();
    renderModal(APP);

    // Stored icon starts selected.
    expect(screen.getByRole('button', { name: '📦' })).toHaveAttribute('aria-pressed', 'true');

    await user.click(screen.getByRole('button', { name: 'None' }));
    await user.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() => {
      const put = calls.find((c) => c.method === 'PUT');
      expect(put).toBeDefined();
      const body = JSON.parse(put!.body!);
      expect(body.name).toBe('Order Tracking');
      expect(body.icon).toBeNull();
    });
  });
});
