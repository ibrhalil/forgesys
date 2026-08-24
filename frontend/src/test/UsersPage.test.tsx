import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { UsersPage } from '../features/users/UsersPage';
import { useAuthStore } from '../store/authStore';
import { useLocaleStore } from '../store/localeStore';

const USERS_PAYLOAD = {
  data: [
    {
      id: 'u-1',
      username: 'jane',
      email: 'jane@acme.dev',
      emailVerified: true,
      firstName: 'Jane',
      lastName: 'Doe',
      enabled: true,
      lockedUntil: null,
      lastLoginAt: null,
      createdDate: '2026-08-01T10:00:00Z',
      roleCount: 2,
      groupCount: 1,
    },
  ],
  meta: { page: 0, pageSize: 10, totalElements: 1, totalPages: 1, hasNext: false, hasPrevious: false },
};

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <UsersPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('UsersPage (create gating)', () => {
  beforeEach(() => {
    useLocaleStore.setState({ locale: 'en' });
    useAuthStore.setState({ hasAuthority: () => true });
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        new Response(JSON.stringify(USERS_PAYLOAD), { status: 200, headers: { 'Content-Type': 'application/json' } }),
      ),
    );
  });
  afterEach(() => vi.unstubAllGlobals());

  it('shows the create action to an iam:user:write holder', async () => {
    renderPage();

    expect(await screen.findByRole('button', { name: '+ New User' })).toBeInTheDocument();
    expect(await screen.findByText('jane@acme.dev')).toBeInTheDocument();
  });

  it('hides the create action without iam:user:write — rows still render', async () => {
    useAuthStore.setState({ hasAuthority: (a: string) => a !== 'iam:user:write' });
    renderPage();

    expect(await screen.findByText('jane@acme.dev')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '+ New User' })).not.toBeInTheDocument();
  });
});
