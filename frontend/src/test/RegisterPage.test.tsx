import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { RegisterPage } from '../features/auth/RegisterPage';
import { useLocaleStore } from '../store/localeStore';

/** POST /api/v1/auth/company/register success payload (K-21 two-phase signup). */
const REGISTER_RESPONSE = {
  companyName: 'Acme',
  subdomain: 'acme',
  adminEmail: 'admin@acme.dev',
  verificationRequired: true,
};

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/register']}>
      <Routes>
        <Route path="/register" element={<RegisterPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('RegisterPage (i18n placeholders + success panel)', () => {
  beforeEach(() => {
    window.localStorage.clear();
    useLocaleStore.setState({ locale: 'en' });
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = String(input);
        if (url === '/api/v1/auth/company/register' && init?.method === 'POST') {
          return new Response(JSON.stringify(REGISTER_RESPONSE), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          });
        }
        return new Response(JSON.stringify({ code: 'resource_not_found' }), { status: 404 });
      }),
    );
  });
  afterEach(() => vi.unstubAllGlobals());

  it('localizes the admin email placeholder (no hardcoded admin@sirket.com)', () => {
    renderPage();

    expect(screen.getByPlaceholderText('admin@company.com')).toBeInTheDocument();
  });

  it('labels the success-panel subdomain line through i18n', async () => {
    const user = userEvent.setup();
    renderPage();

    await user.type(screen.getByPlaceholderText('e.g. Acme Inc.'), 'Acme');
    await user.type(screen.getByPlaceholderText('acme'), 'acme');
    await user.type(screen.getByPlaceholderText('admin@company.com'), 'admin@acme.dev');
    await user.type(screen.getByPlaceholderText('••••••••'), 'secret123');
    await user.click(screen.getByRole('button', { name: /create/i }));

    // Success card: "Subdomain: acme" — the label comes from the dictionary.
    const line = await screen.findByText('Subdomain:', { exact: false });
    expect(line).toBeInTheDocument();
    expect(screen.getByText('acme')).toBeInTheDocument();
  });
});
