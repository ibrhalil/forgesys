import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AppsPage } from '../features/apps/AppsPage';
import { useAuthStore } from '../store/authStore';
import { useLocaleStore } from '../store/localeStore';

/** GET /api/v1/apps PageResponse payload. */
const APPS_PAYLOAD = {
  data: [
    { id: 'app-1', name: 'Order Tracking', description: 'Orders', icon: null, createdDate: '2026-08-01T10:00:00Z', updatedAt: '2026-08-01T10:00:00Z' },
    { id: 'app-2', name: 'Inventory', description: null, icon: null, createdDate: '2026-08-02T10:00:00Z', updatedAt: '2026-08-02T10:00:00Z' },
  ],
  meta: { page: 0, pageSize: 10, totalElements: 2, totalPages: 1, hasNext: false, hasPrevious: false },
};

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <AppsPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('AppsPage', () => {
  beforeEach(() => {
    useLocaleStore.setState({ locale: 'en' });
    useAuthStore.setState({ hasAuthority: () => true });
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => new Response(JSON.stringify(APPS_PAYLOAD), { status: 200, headers: { 'Content-Type': 'application/json' } })),
    );
  });
  afterEach(() => vi.unstubAllGlobals());

  it('renders the app rows from the API', async () => {
    renderPage();

    expect(screen.getByRole('heading', { name: 'Apps' })).toBeInTheDocument();
    expect(await screen.findByText('Order Tracking')).toBeInTheDocument();
    expect(screen.getByText('Inventory')).toBeInTheDocument();
  });

  it('opens the create modal with the name field', async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole('button', { name: '+ New App' }));
    expect(screen.getByLabelText(/name/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Create' })).toBeInTheDocument();
  });
});
