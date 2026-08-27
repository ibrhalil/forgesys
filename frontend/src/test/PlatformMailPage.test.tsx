import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { PlatformMailPage } from '../features/platform/PlatformMailPage';
import { usePlatformAuthStore } from '../store/platformAuthStore';
import { useTenantStore } from '../store/tenantStore';
import { useLocaleStore } from '../store/localeStore';

/**
 * K-51 platform mail testing: info panel renders from the platform endpoint,
 * requests carry NO tenant header (even with an active tenant in the store),
 * preview posts the prefilled sample data and the send button gates on a
 * recipient + posts the test-send body.
 */

const INFO_PAYLOAD = {
  channel: 'SMTP',
  from: 'ForgeSys <no-reply@forgessys.local>',
  defaultLanguage: 'en',
  templatesDir: '',
  templates: [
    { name: 'TENANT_VERIFY', key: 'tenant-verify', subjectTr: 'doğrulayın', subjectEn: 'verify your organization' },
    { name: 'EMAIL_VERIFY', key: 'email-verify', subjectTr: 'doğrulayın', subjectEn: 'verify your email address' },
    { name: 'PASSWORD_RESET', key: 'password-reset', subjectTr: 'şifre sıfırlama', subjectEn: 'password reset' },
  ],
};

const PREVIEW_PAYLOAD = {
  subject: 'ForgeSys — verify your organization',
  bodyHtml: '<html><body><h1>Hello Test</h1></body></html>',
};

const SEND_PAYLOAD = { channel: 'SMTP', recipient: 'dest@example.com', template: 'TENANT_VERIFY', language: 'en' };

interface RecordedCall {
  url: string;
  method: string;
  body: unknown;
  headers: Record<string, string>;
}

let calls: RecordedCall[] = [];
let infoPayload = INFO_PAYLOAD;

function jsonResponse(payload: unknown) {
  return new Response(JSON.stringify(payload), { status: 200, headers: { 'content-type': 'application/json' } });
}

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const utils = render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <PlatformMailPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
  return { ...utils, container: utils.container };
}

describe('PlatformMailPage', () => {
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
        authorities: ['platform:mail:test'],
      },
      hasAuthority: () => true,
    });
    // An active tenant must not leak into the platform request.
    useTenantStore.setState({ tenantId: 'acme' });
    calls = [];
    infoPayload = INFO_PAYLOAD;
    window.localStorage.clear();
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = String(input);
        const method = init?.method ?? 'GET';
        const body = typeof init?.body === 'string' ? JSON.parse(init.body) : null;
        calls.push({ url, method, body, headers: (init?.headers as Record<string, string>) ?? {} });
        if (url.endsWith('/api/v1/platform/mail/info')) return jsonResponse(infoPayload);
        if (url.endsWith('/api/v1/platform/mail/preview')) return jsonResponse(PREVIEW_PAYLOAD);
        if (url.endsWith('/api/v1/platform/mail/test-send')) return jsonResponse(SEND_PAYLOAD);
        return jsonResponse({ code: 'not_found' });
      }),
    );
  });

  afterEach(() => vi.unstubAllGlobals());

  it('renders the active mail config and template defaults', async () => {
    renderPage();

    expect(await screen.findByText('SMTP — real delivery')).toBeInTheDocument();
    expect(screen.getByText('ForgeSys <no-reply@forgessys.local>')).toBeInTheDocument();
    expect(screen.getByText(/jar classpath copy in use/i)).toBeInTheDocument();
    // Prefilled sample data (backend defaults mirrored in the form).
    expect(screen.getByLabelText('First name')).toHaveValue('Test');
    expect(screen.getByLabelText('Organization')).toHaveValue('ForgeSys Test');
  });

  it('calls the platform endpoint without the tenant header', async () => {
    renderPage();

    await screen.findByText('SMTP — real delivery');
    expect(calls.length).toBeGreaterThan(0);
    for (const call of calls) {
      expect(call.headers['X-Tenant-ID']).toBeUndefined();
    }
  });

  it('warns when the active channel does not really deliver', async () => {
    infoPayload = { ...INFO_PAYLOAD, channel: 'LOG' };
    renderPage();

    expect(await screen.findByText(/No real mail leaves this profile/i)).toBeInTheDocument();
  });

  it('previews with the prefilled sample data and renders the result', async () => {
    const user = userEvent.setup();
    renderPage();

    await screen.findByText('SMTP — real delivery');
    await user.click(screen.getByRole('button', { name: 'Preview' }));

    const previewCall = calls.find((c) => c.url.endsWith('/api/v1/platform/mail/preview'));
    expect(previewCall).toBeDefined();
    expect(previewCall?.method).toBe('POST');
    expect(previewCall?.body).toMatchObject({
      template: 'TENANT_VERIFY',
      language: 'en',
      firstName: 'Test',
      organizationName: 'ForgeSys Test',
    });
    expect(await screen.findByText('ForgeSys — verify your organization')).toBeInTheDocument();
    expect(document.querySelector('iframe[title="Preview"]')).not.toBeNull();
  });

  it('gates sending on a recipient and posts the test-send body', async () => {
    const user = userEvent.setup();
    renderPage();

    await screen.findByText('SMTP — real delivery');
    const sendButton = screen.getByRole('button', { name: 'Send test mail' });
    expect(sendButton).toBeDisabled();

    await user.type(screen.getByLabelText('Recipient email'), 'dest@example.com');
    await user.click(sendButton);

    const sendCall = calls.find((c) => c.url.endsWith('/api/v1/platform/mail/test-send'));
    expect(sendCall).toBeDefined();
    expect(sendCall?.body).toMatchObject({ recipient: 'dest@example.com', template: 'TENANT_VERIFY', language: 'en' });
  });
});
