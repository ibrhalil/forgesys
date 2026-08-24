import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { UserDetailPage } from '../features/users/UserDetailPage';
import type { MeResponse } from '../features/auth/types';
import { useAuthStore } from '../store/authStore';
import { useLocaleStore } from '../store/localeStore';

const USER = {
  id: 'u-1',
  email: 'jane@acme.dev',
  username: 'jane',
  firstName: 'Jane',
  lastName: 'Doe',
  enabled: true,
  emailVerified: true,
  lockedUntil: null,
  roles: [{ id: 'r-1', name: 'Developer' }],
  groups: [],
};

const ACTIVITY = {
  createdDate: '2026-08-01T10:00:00Z',
  createdBy: 'admin@acme.dev',
  lastLoginAt: '2026-08-23T09:00:00Z',
  lastFailedLoginAt: null,
  updatedAt: null,
  updatedBy: null,
};

const EMPTY_PAGE = {
  data: [],
  meta: { page: 0, pageSize: 200, totalElements: 0, totalPages: 0, hasNext: false, hasPrevious: false },
};

function renderAt(path: string) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/users/:userId" element={<UserDetailPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('UserDetailPage (edit-mode polish)', () => {
  beforeEach(() => {
    useLocaleStore.setState({ locale: 'en' });
    useAuthStore.setState({ hasAuthority: () => true, user: { id: 'admin' } as MeResponse });
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = String(input);
        const body = url === '/api/v1/users/u-1'
          ? USER
          : url === '/api/v1/users/u-1/activity'
            ? ACTIVITY
            : url === '/api/v1/users/u-1/effective-permissions'
              ? ['iam:user:read']
              : EMPTY_PAGE;
        return new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } });
      }),
    );
  });
  afterEach(() => vi.unstubAllGlobals());

  it('shows the verification status as a badge in edit mode (no disabled switch)', async () => {
    const user = userEvent.setup();
    renderAt('/users/u-1');

    // View mode: the overflow menu is present.
    expect(await screen.findByRole('button', { name: 'Actions' })).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Edit' }));

    // Only the account-enabled setting renders as a switch — verification is a
    // read-only status, shown with the same badge labels as view mode.
    const switches = screen.getAllByRole('switch');
    expect(switches).toHaveLength(1);
    expect(screen.getByRole('switch', { name: 'Account enabled' })).toBeInTheDocument();
    expect(screen.getByText('Verified')).toBeInTheDocument();

    // Roles/groups pickers are seeded from the user's summaries — the current
    // role renders as a labeled chip before any search.
    expect(screen.getByText('Developer')).toBeInTheDocument();
  });

  it('hides the overflow menu while editing and restores it on cancel', async () => {
    const user = userEvent.setup();
    renderAt('/users/u-1');
    expect(await screen.findByRole('button', { name: 'Actions' })).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Edit' }));
    // No overflow (and no Edit) while the form is active — a dirty form must not
    // trigger parallel mutations.
    expect(screen.queryByRole('button', { name: 'Actions' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(screen.getByRole('button', { name: 'Actions' })).toBeInTheDocument();
  });
});
