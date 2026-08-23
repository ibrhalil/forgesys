import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { NoteEditorPage } from '../features/notes/NoteEditorPage';
import { useAuthStore } from '../store/authStore';
import { useLocaleStore } from '../store/localeStore';

const NOTE = {
  id: 'note-1',
  title: 'API design',
  content: '# Heading\n\n- item',
  projectId: 'proj-notes',
  projectName: 'Journal',
  categoryId: 'cat-1',
  categoryName: 'Work',
  pinned: false,
  updatedAt: '2026-08-23T09:00:00Z',
};

const CATEGORIES_PAYLOAD = {
  data: [{ id: 'cat-1', name: 'Work', color: null }],
  meta: { page: 0, pageSize: 100, totalElements: 1, totalPages: 1, hasNext: false, hasPrevious: false },
};

let calls: { url: string; method: string; body?: unknown }[];

function renderAt(path: string) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/notes/:noteId" element={<NoteEditorPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('NoteEditorPage', () => {
  beforeEach(() => {
    useLocaleStore.setState({ locale: 'en' });
    useAuthStore.setState({ hasAuthority: () => true });
    calls = [];
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = String(input);
        calls.push({ url, method: init?.method ?? 'GET', body: init?.body ? JSON.parse(String(init.body)) : undefined });
        const payload = url.startsWith(`/api/v1/notes/${NOTE.id}`)
          ? NOTE
          : url.startsWith('/api/v1/note-categories')
            ? CATEGORIES_PAYLOAD
            : null;
        if (payload === null) {
          return new Response(JSON.stringify({ code: 'resource_not_found', message: 'nope' }), { status: 404 });
        }
        return new Response(JSON.stringify(payload), { status: 200, headers: { 'Content-Type': 'application/json' } });
      }),
    );
  });
  afterEach(() => vi.unstubAllGlobals());

  it('loads the note into the form and saves an update via PUT', async () => {
    const user = userEvent.setup();
    renderAt(`/notes/${NOTE.id}`);

    const title = await screen.findByDisplayValue('API design');
    expect(title).toBeInTheDocument();

    await user.clear(title);
    await user.type(title, 'API design v2');
    await user.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() => {
      const put = calls.find((c) => c.method === 'PUT');
      expect(put?.url).toBe(`/api/v1/notes/${NOTE.id}`);
      expect(put?.body).toMatchObject({ title: 'API design v2', content: NOTE.content, categoryId: 'cat-1' });
    });
  });

  it('toggles to a markdown preview that renders headings but never raw HTML', async () => {
    const user = userEvent.setup();
    renderAt(`/notes/${NOTE.id}`);
    await screen.findByDisplayValue('API design');

    await user.click(screen.getByRole('button', { name: 'Preview' }));

    // markdown renders structurally (an actual <h1>, not raw HTML injection)
    const heading = await screen.findByRole('heading', { level: 1, name: 'Heading' });
    expect(heading).toBeInTheDocument();

    // raw HTML in content must NOT become markup — inject and re-preview
    await user.click(screen.getByRole('button', { name: 'Edit' }));
    const textarea = screen.getByPlaceholderText(/Write markdown/) as HTMLTextAreaElement;
    await user.clear(textarea);
    await user.type(textarea, '<img src=x onerror="alert(1)">');
    await user.click(screen.getByRole('button', { name: 'Preview' }));

    await waitFor(() => {
      expect(document.querySelector('img')).toBeNull();
      expect(document.querySelector('script')).toBeNull();
    });
  });

  it('creates a new note via POST from the /notes/new route', async () => {
    const user = userEvent.setup();
    const created = { ...NOTE, id: 'note-new', title: 'Fresh note' };
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = String(input);
        calls.push({ url, method: init?.method ?? 'GET', body: init?.body ? JSON.parse(String(init.body)) : undefined });
        const payload = url.startsWith('/api/v1/note-categories')
          ? CATEGORIES_PAYLOAD
          : url === '/api/v1/notes' && init?.method === 'POST'
            ? created
            : null;
        if (payload === null) {
          return new Response(JSON.stringify({ code: 'resource_not_found' }), { status: 404 });
        }
        return new Response(JSON.stringify(payload), { status: 200, headers: { 'Content-Type': 'application/json' } });
      }),
    );

    renderAt('/notes/new');
    const title = await screen.findByPlaceholderText('e.g. Meeting notes');
    await user.type(title, 'Fresh note');
    await user.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() => {
      const post = calls.find((c) => c.method === 'POST');
      expect(post?.url).toBe('/api/v1/notes');
      expect(post?.body).toMatchObject({ title: 'Fresh note', content: '' });
    });
  });

  it('carries the ?projectId= param into the create POST (project panel entry)', async () => {
    const user = userEvent.setup();
    const created = { ...NOTE, id: 'note-2', title: 'Panel note' };
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = String(input);
        calls.push({ url, method: init?.method ?? 'GET', body: init?.body ? JSON.parse(String(init.body)) : undefined });
        const payload = url.startsWith('/api/v1/note-categories')
          ? CATEGORIES_PAYLOAD
          : url === '/api/v1/notes' && init?.method === 'POST'
            ? created
            : null;
        if (payload === null) {
          return new Response(JSON.stringify({ code: 'resource_not_found' }), { status: 404 });
        }
        return new Response(JSON.stringify(payload), { status: 200, headers: { 'Content-Type': 'application/json' } });
      }),
    );

    renderAt('/notes/new?projectId=proj-notes');
    const title = await screen.findByPlaceholderText('e.g. Meeting notes');
    await user.type(title, 'Panel note');
    await user.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() => {
      const post = calls.find((c) => c.method === 'POST');
      expect(post?.body).toMatchObject({ title: 'Panel note', projectId: 'proj-notes' });
    });
  });
});
