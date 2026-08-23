import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { NotesPage } from '../features/notes/NotesPage';
import { useAuthStore } from '../store/authStore';
import { useLocaleStore } from '../store/localeStore';

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
let noteQuery: URLSearchParams | null;

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
    noteQuery = null;
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
        if (url.startsWith('/api/v1/notes?')) {
          noteQuery = new URLSearchParams(url.split('?')[1]);
        }
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
      expect(urls.some((u) => u.includes('sort=updatedAt%2Cdesc'))).toBe(true);
      expect(urls.some((u) => u.includes('sort=updatedDate'))).toBe(false);
    });
  });

  it('re-queries with the categoryId filter when a category is picked', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('API design');

    await user.click(screen.getByText('All categories'));
    await user.click(await screen.findByRole('option', { name: 'Work' }));

    await waitFor(() => {
      expect(noteQuery?.get('categoryId')).toBe('cat-1');
    });
  });

  it('re-queries with pinned=true when the pinned toggle is on', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('API design');

    await user.click(screen.getByRole('button', { name: /Pinned/ }));

    await waitFor(() => {
      expect(noteQuery?.get('pinned')).toBe('true');
    });
  });
});
