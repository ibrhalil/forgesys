import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { UserPicker } from '../components/pickers/UserPicker';
import { RelationPicker } from '../features/custom-apps/components/RelationPicker';
import type { CustomAppProperty } from '../features/custom-apps/types';
import { useLocaleStore } from '../store/localeStore';

const USERS_PAYLOAD = {
  data: [
    { id: 'u-jane', email: 'jane@acme.com' },
    { id: 'u-john', email: 'john@acme.com' },
  ],
  meta: { page: 0, pageSize: 20, totalElements: 2, totalPages: 1, hasNext: false, hasPrevious: false },
};

const TARGET_APP_ID = '88888888-8888-8888-8888-888888888888';

const TARGET_DETAIL = {
  id: TARGET_APP_ID,
  name: 'Customers',
  properties: [
    { id: 'p-name', customAppId: TARGET_APP_ID, name: 'Name', type: 'TEXT', config: null, required: false, position: 0 },
  ],
  views: [],
};

const TARGET_RECORDS = {
  data: [
    { id: 't-1', customAppId: TARGET_APP_ID, values: { 'p-name': 'Acme Ltd' }, createdDate: '2026-08-01T00:00:00Z', updatedAt: '', createdBy: 'u' },
    { id: 't-2', customAppId: TARGET_APP_ID, values: { 'p-name': 'Globex' }, createdDate: '2026-08-02T00:00:00Z', updatedAt: '', createdBy: 'u' },
  ],
  meta: { page: 0, pageSize: 100, totalElements: 2, totalPages: 1, hasNext: false, hasPrevious: false },
};

const RELATION_PROP: CustomAppProperty = {
  id: 'p-cust',
  customAppId: '99999999-9999-9999-9999-999999999999',
  name: 'Customer',
  type: 'RELATION',
  config: { targetCustomAppId: TARGET_APP_ID },
  required: false,
  position: 1,
};

let calls: { method: string; url: string }[] = [];

describe('UserPicker', () => {
  beforeEach(() => {
    useLocaleStore.setState({ locale: 'en' });
    calls = [];
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = String(input);
        calls.push({ method: 'GET', url });
        return new Response(JSON.stringify(USERS_PAYLOAD), { status: 200, headers: { 'Content-Type': 'application/json' } });
      }),
    );
  });
  afterEach(() => vi.unstubAllGlobals());

  it('typeahead-loads directory users and emits the picked id', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<UserPicker value={null} onChange={onChange} debounceMs={0} />);

    const combobox = screen.getByRole('combobox');
    await user.click(combobox);
    await user.type(combobox, 'jane');
    // Async typeahead hits the user directory with the typed input as q.
    await waitFor(() => expect(calls.some((c) => c.url.includes('/api/v1/users') && c.url.includes('q=jane'))).toBe(true));
    await user.click(await screen.findByRole('option', { name: 'jane@acme.com' }));

    expect(onChange).toHaveBeenCalledWith('u-jane');
  });

  it('shows the provided valueLabel for the current value', () => {
    render(<UserPicker value={'u-jane'} valueLabel="jane@acme.com" onChange={vi.fn()} debounceMs={0} />);
    expect(screen.getByText('jane@acme.com')).toBeInTheDocument();
  });
});

describe('RelationPicker', () => {
  beforeEach(() => {
    useLocaleStore.setState({ locale: 'en' });
    calls = [];
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = String(input);
        calls.push({ method: 'GET', url });
        const body = url.includes('/records') ? TARGET_RECORDS : TARGET_DETAIL;
        return new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } });
      }),
    );
  });
  afterEach(() => vi.unstubAllGlobals());

  it('offers target-customApp records titled by their first TEXT property', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={client}>
        <RelationPicker property={RELATION_PROP} value={null} onChange={onChange} />
      </QueryClientProvider>,
    );

    // Target detail + records are fetched through the shared customApps cache keys.
    await waitFor(() =>
      expect(calls.some((c) => c.url === `/api/v1/custom-apps/${TARGET_APP_ID}/records` || c.url.includes(`/api/v1/custom-apps/${TARGET_APP_ID}/records?`))).toBe(true),
    );

    const combobox = await screen.findByRole('combobox');
    await user.click(combobox);
    await user.type(combobox, 'Acme');
    await user.click(await screen.findByRole('option', { name: 'Acme Ltd' }));

    expect(onChange).toHaveBeenCalledWith('t-1');
  });

  it('renders disabled when the property lost its target config', () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={client}>
        <RelationPicker property={{ ...RELATION_PROP, config: null }} value={null} onChange={vi.fn()} />
      </QueryClientProvider>,
    );
    expect(screen.getByText(/no target app configured/i)).toBeInTheDocument();
  });
});
