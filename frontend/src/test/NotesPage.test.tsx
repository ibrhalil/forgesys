import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { NotesPage } from '../features/notes/NotesPage';
import { useAuthStore } from '../store/authStore';
import { useLocaleStore } from '../store/localeStore';
import { decodedSq } from './sqUrl';

const NOTES_PAYLOAD = {
  data: [
    {
      id: 'note-1',
      title: 'API design',
      content: '# Heading',
      categoryId: 'cat-1',
      categoryName: 'Work',
      pinned: true,
      updatedAt: '2026-08-23T09:00:00Z',
    },
    {
      id: 'note-2',
      title: 'Groceries',
      content: 'milk',
      categoryId: null,
      categoryName: null,
      pinned: false,
      updatedAt: '2026-08-22T09:00:00Z',
    },
  ],
  meta: { page: 0, pageSize: 10, totalElements: 2, totalPages: 1, hasNext: false, hasPrevious: false },
};

const CATEGORIES_PAYLOAD = {
  data: [
    { id: 'cat-1', name: 'Work', color: null },
    { id: 'cat-2', name: 'Archive', color: null },
  ],
  meta: { page: 0, pageSize: 100, totalElements: 2, totalPages: 1, hasNext: false, hasPrevious: false },
};

let urls: string[];

/** Decoded sq states of the recorded /notes calls (K-55 wire-flip). */
function noteStates() {
  return urls.filter((u) => u.startsWith('/api/v1/notes?')).map(decodedSq);
}

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <NotesPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('NotesPage', () => {
  beforeEach(() => {
    useLocaleStore.setState({ locale: 'en' });
    useAuthStore.setState({ hasAuthority: () => true });
    urls = [];
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = String(input);
        urls.push(url);
        const payload = url.startsWith('/api/v1/notes?')
          ? NOTES_PAYLOAD
          : url.startsWith('/api/v1/note-categories')
            ? CATEGORIES_PAYLOAD
            : { data: [], meta: { page: 0, pageSize: 0, totalElements: 0, totalPages: 0, hasNext: false, hasPrevious: false } };
        return new Response(JSON.stringify(payload), { status: 200, headers: { 'Content-Type': 'application/json' } });
      }),
    );
  });
  afterEach(() => vi.unstubAllGlobals());

  it('renders note rows with category chips and the pinned indicator', async () => {
    renderPage();

    expect(await screen.findByText('API design')).toBeInTheDocument();
    expect(screen.getByText('Groceries')).toBeInTheDocument();
    expect(screen.getByText('Work')).toBeInTheDocument();
    // the pinned note carries the accessible pinned label
    expect(screen.getByLabelText('Pinned')).toBeInTheDocument();
  });

  it('queries with the whitelisted updatedAt sort (entity attribute, not the DTO wire name)', async () => {
    renderPage();
    await screen.findByText('API design');

    await waitFor(() => {
      const states = noteStates();
      expect(states.some((s) => s?.sorts[0]?.field === 'updatedAt' && s.sorts[0].dir === 'desc')).toBe(true);
      expect(states.some((s) => s?.sorts?.some((x) => x.field === 'updatedDate'))).toBe(false);
    });
  });

  it('re-queries with the categoryId filter when a category is picked', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('API design');

    await user.click(screen.getByText('All categories'));
    await user.click(await screen.findByRole('option', { name: 'Work' }));

    await waitFor(() => {
      expect(noteStates().some((s) => s?.filters?.some(
        (f) => f.field === 'categoryId' && f.operator === 'EQ' && f.values.includes('cat-1')))).toBe(true);
    });
  });

  it('re-queries with pinned=true when the pinned toggle is on', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('API design');

    await user.click(screen.getByRole('button', { name: /Pinned/ }));

    await waitFor(() => {
      expect(noteStates().some((s) => s?.filters?.some(
        (f) => f.field === 'pinned' && f.operator === 'EQ' && f.values.includes('true')))).toBe(true);
    });
  });

  // Two-page payload so the pager's Next button is enabled; extra filters must
  // reset the page or page 2 + a narrow filter shows an empty table.
  function stubPagedNotes() {
    const paged = {
      ...NOTES_PAYLOAD,
      meta: { ...NOTES_PAYLOAD.meta, totalElements: 15, totalPages: 2 },
    };
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = String(input);
        urls.push(url);
        const payload = url.startsWith('/api/v1/notes?')
          ? paged
          : url.startsWith('/api/v1/note-categories')
            ? CATEGORIES_PAYLOAD
            : { data: [], meta: { page: 0, pageSize: 0, totalElements: 0, totalPages: 0, hasNext: false, hasPrevious: false } };
        return new Response(JSON.stringify(payload), { status: 200, headers: { 'Content-Type': 'application/json' } });
      }),
    );
  }

  it('resets to page 0 when the category filter changes on a later page', async () => {
    const user = userEvent.setup();
    stubPagedNotes();
    renderPage();
    await screen.findByText('API design');

    await user.click(screen.getByRole('button', { name: 'Next' }));
    await waitFor(() => expect(noteStates().some((s) => s?.page === 1)).toBe(true));

    await user.click(screen.getByText('All categories'));
    await user.click(await screen.findByRole('option', { name: 'Work' }));

    await waitFor(() => {
      expect(noteStates().some((s) => s?.page === 0 && s?.filters?.some(
        (f) => f.field === 'categoryId' && f.values.includes('cat-1')))).toBe(true);
    });
  });

  it('resets to page 0 when the pinned filter toggles on a later page', async () => {
    const user = userEvent.setup();
    stubPagedNotes();
    renderPage();
    await screen.findByText('API design');

    await user.click(screen.getByRole('button', { name: 'Next' }));
    await waitFor(() => expect(noteStates().some((s) => s?.page === 1)).toBe(true));

    await user.click(screen.getByRole('button', { name: /Pinned/ }));

    await waitFor(() => {
      expect(noteStates().some((s) => s?.page === 0 && s?.filters?.some(
        (f) => f.field === 'pinned' && f.values.includes('true')))).toBe(true);
    });
  });

  it('marks the pinned filter button as pressed', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('API design');

    const pinnedBtn = screen.getByRole('button', { name: /Pinned/ });
    expect(pinnedBtn).toHaveAttribute('aria-pressed', 'false');
    await user.click(pinnedBtn);
    expect(pinnedBtn).toHaveAttribute('aria-pressed', 'true');
  });
});
