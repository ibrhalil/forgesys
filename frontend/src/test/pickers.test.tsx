import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { RolePicker } from '../components/pickers/RolePicker';
import { AppPicker } from '../components/pickers/AppPicker';
import { useLocaleStore } from '../store/localeStore';

const ROLES_PAYLOAD = {
  data: [
    { id: 'r-1', name: 'Developer', description: null, allPermissions: false, permissions: [] },
    { id: 'r-2', name: 'Ops', description: null, allPermissions: false, permissions: [] },
  ],
  meta: { page: 0, pageSize: 20, totalElements: 2, totalPages: 1, hasNext: false, hasPrevious: false },
};

const APPS_PAYLOAD = {
  data: [
    { id: 'app-1', name: 'Order Tracking', createdDate: '2026-08-01T10:00:00Z', updatedAt: '2026-08-01T10:00:00Z' },
    { id: 'app-2', name: 'Inventory', createdDate: '2026-08-02T10:00:00Z', updatedAt: '2026-08-02T10:00:00Z' },
  ],
  meta: { page: 0, pageSize: 20, totalElements: 2, totalPages: 1, hasNext: false, hasPrevious: false },
};

let calls: { url: string }[];

/** Roles list stub — asserts the q param lands on the endpoint. */
function stubRoles() {
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      calls.push({ url });
      return new Response(JSON.stringify(ROLES_PAYLOAD), { status: 200, headers: { 'Content-Type': 'application/json' } });
    }),
  );
}

describe('RolePicker', () => {
  beforeEach(() => {
    useLocaleStore.setState({ locale: 'en' });
    calls = [];
  });
  afterEach(() => vi.unstubAllGlobals());

  it('single mode: searches with q and emits the picked id', async () => {
    stubRoles();
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<RolePicker value={null} onChange={onChange} debounceMs={0} />);

    const combobox = screen.getByRole('combobox');
    await user.click(combobox);
    await user.type(combobox, 'dev');
    await waitFor(() => {
      expect(calls.some((c) => c.url.startsWith('/api/v1/roles') && c.url.includes('q=dev'))).toBe(true);
    });

    await user.click(await screen.findByRole('option', { name: 'Developer' }));
    expect(onChange).toHaveBeenCalledWith('r-1');
  });

  it('multi mode: renders the seed chip and appends searched picks', async () => {
    stubRoles();
    const user = userEvent.setup();
    const onValuesChange = vi.fn();
    render(
      <RolePicker
        isMulti
        values={['r-1']}
        selectedOptions={[{ value: 'r-1', label: 'Developer' }]}
        onValuesChange={onValuesChange}
        debounceMs={0}
      />,
    );

    // The seed keeps the current selection labeled before any search happens.
    expect(screen.getByText('Developer')).toBeInTheDocument();

    const combobox = screen.getByRole('combobox');
    await user.click(combobox);
    await user.click(await screen.findByRole('option', { name: 'Ops' }));

    expect(onValuesChange).toHaveBeenCalledWith(['r-1', 'r-2']);
  });
});

describe('AppPicker', () => {
  beforeEach(() => {
    useLocaleStore.setState({ locale: 'en' });
    calls = [];
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = String(input);
        calls.push({ url });
        return new Response(JSON.stringify(APPS_PAYLOAD), { status: 200, headers: { 'Content-Type': 'application/json' } });
      }),
    );
  });
  afterEach(() => vi.unstubAllGlobals());

  it('excludes the configured ids (self-app) from the results', async () => {
    const user = userEvent.setup();
    render(<AppPicker value={null} onChange={vi.fn()} excludeIds={['app-1']} debounceMs={0} />);

    const combobox = screen.getByRole('combobox');
    await user.click(combobox);
    // Menu first open loads the empty-input page instantly (no debounce window).
    expect(await screen.findByRole('option', { name: 'Inventory' })).toBeInTheDocument();
    expect(screen.queryByRole('option', { name: 'Order Tracking' })).not.toBeInTheDocument();
  });
});
