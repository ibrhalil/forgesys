import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { PlatformServiceAccountsPage } from '../features/platform/PlatformServiceAccountsPage';
import { useLocaleStore } from '../store/localeStore';

/**
 * K-50 service accounts: creating an account shows the raw key EXACTLY once in
 * a copy modal; the list itself never carries it (raw/hash absent from the wire
 * list response by backend design — here we assert the modal lifecycle).
 */

const RAW_KEY = 'ABCD2345_' + 's'.repeat(43);

const LIST_PAYLOAD = {
  data: [],
  meta: { page: 0, pageSize: 10, totalElements: 0, totalPages: 0, hasNext: false, hasPrevious: false },
};

const CREATED_PAYLOAD = {
  id: 'k-1',
  accountId: 'sa-1',
  name: 'CI Agent',
  scopes: ['platform:company:read'],
  keyPrefix: 'ABCD2345',
  expiresAt: null,
  rawKey: RAW_KEY,
};

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <PlatformServiceAccountsPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('PlatformServiceAccountsPage', () => {
  beforeEach(() => {
    useLocaleStore.setState({ locale: 'en' });
    window.localStorage.clear();
    vi.stubGlobal('fetch', vi.fn(async (_input: RequestInfo | URL, init?: RequestInit) => {
      const isCreate = init?.method === 'POST';
      const payload = isCreate ? CREATED_PAYLOAD : LIST_PAYLOAD;
      return new Response(JSON.stringify(payload), {
        status: isCreate ? 201 : 200,
        headers: { 'content-type': 'application/json' },
      });
    }));
  });

  afterEach(() => vi.unstubAllGlobals());

  it('shows the raw key exactly once after creation and discards it on close', async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole('button', { name: /create service account/i }));

    await user.type(screen.getByLabelText(/^name$/i), 'CI Agent');
    // Pick a scope in the multi select.
    const combobox = screen.getByRole('combobox');
    await user.click(combobox);
    await user.click(await screen.findByRole('option', { name: 'platform:company:read' }));
    await user.click(screen.getByRole('button', { name: /^create$/i }));

    // The one-time modal shows the raw key with the copy action.
    expect(await screen.findByText(RAW_KEY)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /copy/i })).toBeInTheDocument();

    // The footer "Close" (the header X also carries aria-label Close) discards the key.
    const closeButtons = screen.getAllByRole('button', { name: /close/i });
    await user.click(closeButtons[closeButtons.length - 1]);
    expect(screen.queryByText(RAW_KEY)).not.toBeInTheDocument();
  });
});
