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

function json(payload: unknown, status = 200) {
  return new Response(JSON.stringify(payload), { status, headers: { 'Content-Type': 'application/json' } });
}

function renderAt(path: string) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          {/* Mirror app/Routes.ts topology: /notes/new is a STATIC route (no
              noteId param) — the dynamic :noteId route must not capture it. */}
          <Route path="/notes/new" element={<NoteEditorPage />} />
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
    // Regression: create mode must never fall through to an update call.
    expect(calls.find((c) => c.method === 'PUT')).toBeUndefined();
  });

  it('renders the pin switch in the form and Save in the editor footer', async () => {
    const user = userEvent.setup();
    renderAt(`/notes/${NOTE.id}`);
    await screen.findByDisplayValue('API design');

    // Pin is a boolean-setting Toggle in the meta row (part of the draft), not a RowMenu item.
    const pinSwitch = screen.getByRole('switch', { name: 'Pinned' });
    expect(pinSwitch).toHaveAttribute('aria-checked', 'false');
    await user.click(pinSwitch);
    expect(pinSwitch).toHaveAttribute('aria-checked', 'true');

    // Save sits bottom-right of the editing surface (justify-end footer), not in the page head.
    const save = screen.getByRole('button', { name: 'Save' });
    expect(save.closest('div')).toHaveClass('justify-end');

    // The toggle is part of the draft — it persists via Save.
    await user.click(save);
    await waitFor(() => {
      const put = calls.find((c) => c.method === 'PUT');
      expect(put?.body).toMatchObject({ pinned: true });
    });
  });

  it('keeps the category labeled while options load late (fallback option)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = String(input);
        if (url.startsWith(`/api/v1/notes/${NOTE.id}`)) return json(NOTE);
        if (url.startsWith('/api/v1/note-categories')) {
          // Categories hang: the fallback keeps the note's categoryName selected
          // instead of flashing the select as uncategorized.
          return new Promise<Response>(() => {});
        }
        return new Response(JSON.stringify({ code: 'resource_not_found' }), { status: 404 });
      }),
    );

    renderAt(`/notes/${NOTE.id}`);
    await screen.findByDisplayValue('API design');

    expect(screen.getByText('Work')).toBeInTheDocument();
  });

  it('shows the category creation failure inline under the select', async () => {
    const user = userEvent.setup();
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = String(input);
        calls.push({ url, method: init?.method ?? 'GET', body: init?.body ? JSON.parse(String(init.body)) : undefined });
        if (url.startsWith(`/api/v1/notes/${NOTE.id}`)) return json(NOTE);
        if (url.startsWith('/api/v1/note-categories') && init?.method === 'POST') {
          // Field-level validation — the global onError stays silent for fields[],
          // so the form must render it inline.
          return json(
            { code: 'validation_error', message: 'Invalid', fields: [{ field: 'name', message: 'Name already exists' }] },
            400,
          );
        }
        if (url.startsWith('/api/v1/note-categories')) return json(CATEGORIES_PAYLOAD);
        return new Response(JSON.stringify({ code: 'resource_not_found' }), { status: 404 });
      }),
    );

    renderAt(`/notes/${NOTE.id}`);
    await screen.findByDisplayValue('API design');

    const combo = screen.getByRole('combobox', { name: /category/i });
    await user.click(combo);
    await user.type(combo, 'Fresh');
    await user.click(await screen.findByText('Create category: Fresh'));

    expect(await screen.findByText('Name already exists')).toBeInTheDocument();
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
          : url === '/api/v1/projects/proj-notes'
            ? { id: 'proj-notes', name: 'Journal', description: null, type: 'NOTES', parentProjectId: null, isDefault: false }
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

    // The ProjectPicker seed resolves the container's name from GET /projects/{id}.
    expect(await screen.findByText('Journal')).toBeInTheDocument();

    await user.type(title, 'Panel note');
    await user.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() => {
      const post = calls.find((c) => c.method === 'POST');
      expect(post?.body).toMatchObject({ title: 'Panel note', projectId: 'proj-notes' });
    });
  });
});
