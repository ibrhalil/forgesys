import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { RecordList } from '../features/custom-apps/components/RecordList';
import { RecordGallery } from '../features/custom-apps/components/RecordGallery';
import { cellDisplay } from '../features/custom-apps/cellValue';
import type { CustomAppDetail, CustomAppRecord } from '../features/custom-apps/types';
import { useAuthStore } from '../store/authStore';
import { useLocaleStore } from '../store/localeStore';

const APP_ID = '44444444-4444-4444-4444-444444444444';

const APP: CustomAppDetail = {
  id: APP_ID,
  projectId: 'proj-1',
  projectName: 'Genel',
  name: 'Orders',
  description: null,
  icon: null,
  createdDate: '2026-08-01T10:00:00Z',
  updatedAt: '2026-08-01T10:00:00Z',
  properties: [
    { id: 'p-title', customAppId: APP_ID, name: 'Title', type: 'TEXT', config: null, required: false, position: 0 },
    { id: 'p-note', customAppId: APP_ID, name: 'Note', type: 'TEXT', config: null, required: false, position: 1 },
  ],
  views: [],
};

const record = (id: string, title: string, note: string): CustomAppRecord => ({
  id,
  customAppId: APP_ID,
  values: { 'p-title': title, 'p-note': note },
  createdDate: '2026-08-10T09:00:00Z',
  updatedAt: '2026-08-10T09:00:00Z',
  createdBy: 'u1',
});

const RECORDS: CustomAppRecord[] = [
  record('r-1', 'First order', 'urgent'),
  record('r-2', 'Second order', 'later'),
  record('r-3', 'Third order', ''),
];

const onRequestDelete = vi.fn();
const onRequestEdit = vi.fn();

function renderList(records: CustomAppRecord[] = RECORDS) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <RecordList customApp={APP} records={records} isLoading={false} resolve={cellDisplay} onRequestDelete={onRequestDelete} onRequestEdit={onRequestEdit} />
    </QueryClientProvider>,
  );
}

function renderGallery(records: CustomAppRecord[] = RECORDS) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <RecordGallery customApp={APP} records={records} isLoading={false} resolve={cellDisplay} onRequestDelete={onRequestDelete} onRequestEdit={onRequestEdit} />
    </QueryClientProvider>,
  );
}

describe('RecordList', () => {
  beforeEach(() => {
    useLocaleStore.setState({ locale: 'en' });
    useAuthStore.setState({ hasAuthority: () => true });
    onRequestDelete.mockClear();
    onRequestEdit.mockClear();
  });
  afterEach(() => vi.clearAllMocks());

  it('renders compact rows with title, muted summary and pagination', async () => {
    const many = Array.from({ length: 12 }, (_, i) => record(`r-${i + 1}`, `Order ${i + 1}`, `n${i}`));
    renderList(many);

    // Compact row: title + "Note: value" summary for filled fields.
    expect(screen.getByText('Order 1')).toBeInTheDocument();
    expect(screen.getByText('Note: n0')).toBeInTheDocument();
    // 10 rows on the first page; the 11th is behind pagination.
    expect(screen.queryByText('Order 11')).not.toBeInTheDocument();

    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: 'Next' }));
    expect(await screen.findByText('Order 11')).toBeInTheDocument();
  });

  it('requests deletion through the row actions menu', async () => {
    const user = userEvent.setup();
    renderList();

    await user.click(screen.getAllByRole('button', { name: 'Actions' })[0]);
    await user.click(await screen.findByRole('menuitem', { name: 'Delete' }));
    expect(onRequestDelete).toHaveBeenCalledWith(RECORDS[0]);
  });
});

describe('RecordGallery', () => {
  beforeEach(() => {
    useLocaleStore.setState({ locale: 'en' });
    useAuthStore.setState({ hasAuthority: () => true });
    onRequestDelete.mockClear();
    onRequestEdit.mockClear();
  });
  afterEach(() => vi.clearAllMocks());

  it('renders cards with title and labeled property rows', () => {
    renderGallery();

    expect(screen.getByText('First order')).toBeInTheDocument();
    expect(screen.getByText('Second order')).toBeInTheDocument();
    // Property row label + value.
    expect(screen.getAllByText('Note').length).toBeGreaterThan(0);
    expect(screen.getByText('urgent')).toBeInTheDocument();
  });

  it('requests deletion through the card menu', async () => {
    const user = userEvent.setup();
    renderGallery();

    await user.click(screen.getAllByRole('button', { name: 'Actions' })[0]);
    await user.click(await screen.findByRole('menuitem', { name: 'Delete' }));
    expect(onRequestDelete).toHaveBeenCalledWith(RECORDS[0]);
  });

  it('paginates through the shared TablePagination footer (fixed card page size)', async () => {
    // 15 records > GALLERY_PAGE_SIZE (12): first page shows 12, Next reveals the rest.
    const many = Array.from({ length: 15 }, (_, i) => record(`r-${i + 1}`, `Order ${i + 1}`, `n${i}`));
    const user = userEvent.setup();
    renderGallery(many);

    expect(screen.getByText('1–12 / 15')).toBeInTheDocument();
    // Fixed page size — the rows-per-page selector must not render.
    expect(screen.queryByRole('group', { name: 'Rows per page' })).not.toBeInTheDocument();
    expect(screen.queryByText('Rows per page')).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Next' }));
    expect(await screen.findByText('Order 13')).toBeInTheDocument();
    expect(screen.getByText('13–15 / 15')).toBeInTheDocument();
    expect(screen.getByText('2 / 2')).toBeInTheDocument();
  });
});
