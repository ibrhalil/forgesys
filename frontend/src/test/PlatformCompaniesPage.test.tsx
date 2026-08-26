import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { PlatformCompaniesPage } from '../features/platform/PlatformCompaniesPage';
import { usePlatformAuthStore } from '../store/platformAuthStore';
import { useTenantStore } from '../store/tenantStore';
import { useLocaleStore } from '../store/localeStore';

/**
 * K-50 platform companies list: rows render from the platform endpoint, the
 * request carries NO tenant header (even with an active tenant in the store),
 * and status renders localized badges.
 */

const COMPANIES_PAYLOAD = {
  data: [
    { id: 'c-1', name: 'Acme Corp', subdomain: 'acme', status: 'ACTIVE' },
    { id: 'c-2', name: 'Globex', subdomain: 'globex', status: 'SUSPENDED' },
  ],
  meta: { page: 0, pageSize: 10, totalElements: 2, totalPages: 1, hasNext: false, hasPrevious: false },
};

let requestHeaders: Record<string, string> | null = null;

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <PlatformCompaniesPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('PlatformCompaniesPage', () => {
  beforeEach(() => {
    useLocaleStore.setState({ locale: 'en' });
    usePlatformAuthStore.setState({
      isAuthenticated: true,
      isLoading: false,
      user: {
        userId: 'u-root',
        email: 'root@platform.dev',
        displayName: 'Root',
        userType: 'HUMAN',
        authorities: ['platform:company:read'],
      },
      hasAuthority: () => true,
    });
    // An active tenant must not leak into the platform request.
    useTenantStore.setState({ tenantId: 'acme' });
    requestHeaders = null;
    window.localStorage.clear();
    vi.stubGlobal(
      'fetch',
      vi.fn(async (_input: RequestInfo | URL, init?: RequestInit) => {
        requestHeaders = (init?.headers as Record<string, string>) ?? {};
        return new Response(JSON.stringify(COMPANIES_PAYLOAD), {
          status: 200,
          headers: { 'content-type': 'application/json' },
        });
      }),
    );
  });

  afterEach(() => vi.unstubAllGlobals());

  it('renders companies with localized status badges', async () => {
    renderPage();

    expect(await screen.findByText('Acme Corp')).toBeInTheDocument();
    expect(screen.getByText('globex')).toBeInTheDocument();
    expect(screen.getByText('Active')).toBeInTheDocument();
    expect(screen.getByText('Suspended')).toBeInTheDocument();
  });

  it('calls the platform endpoint without the tenant header', async () => {
    renderPage();

    await screen.findByText('Acme Corp');
    expect(requestHeaders?.['X-Tenant-ID']).toBeUndefined();
  });
});
