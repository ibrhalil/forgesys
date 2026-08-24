import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { GroupDetailPage } from '../features/groups/GroupDetailPage';
import { useAuthStore } from '../store/authStore';
import { useLocaleStore } from '../store/localeStore';

const GROUP = {
  id: 'g-1',
  name: 'Developers',
  description: null,
  active: true,
  roles: [{ id: 'r-1', name: 'Developer' }],
  members: [{ id: 'u-1', email: 'jane@acme.dev' }],
  memberCount: 1,
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
          <Route path="/groups/:groupId" element={<GroupDetailPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('GroupDetailPage (write gating)', () => {
  beforeEach(() => {
    useLocaleStore.setState({ locale: 'en' });
    useAuthStore.setState({ hasAuthority: () => true });
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = String(input);
        const body = url === `/api/v1/groups/${GROUP.id}`
          ? GROUP
          : url === `/api/v1/groups/${GROUP.id}/effective-permissions`
            ? ['iam:user:read']
            : EMPTY_PAGE;
        return new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } });
      }),
    );
  });
  afterEach(() => vi.unstubAllGlobals());

  it('shows the multi-select editing surfaces to an iam:group:write holder', async () => {
    renderAt(`/groups/${GROUP.id}`);

    // The effective-permissions panel renders once the group query resolves.
    expect(
      await screen.findByRole('heading', { name: /Effective permissions this group grants/ }),
    ).toBeInTheDocument();
    // Two AssignSections (roles + members), each with a picker and a Save.
    expect(await screen.findAllByRole('combobox')).toHaveLength(2);
    expect(screen.getAllByRole('button', { name: 'Save' })).toHaveLength(2);
  });

  it('renders read-only badges without pickers or Save for a read-only viewer', async () => {
    useAuthStore.setState({ hasAuthority: (a: string) => a !== 'iam:group:write' });
    renderAt(`/groups/${GROUP.id}`);

    // Current assignments visible as badges — no editing surfaces, no Save (403 trap).
    expect(await screen.findByText('Developer')).toBeInTheDocument();
    expect(screen.getByText('jane@acme.dev')).toBeInTheDocument();
    expect(screen.queryByRole('combobox')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument();
  });
});
