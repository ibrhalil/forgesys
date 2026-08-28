import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useListPageState } from '../lib/useListPageState';
import { encodeSearchQuery, decodeSearchQuery, type SearchQueryState } from '../lib/searchQuery';

/**
 * Unit tests for the list-page scaffold hook (K-39): sort toggle semantics,
 * page-reset contracts (sort / page-size / debounced search) and defaults.
 */

describe('useListPageState', () => {
  it('returns the defaults', () => {
    const { result } = renderHook(() =>
      useListPageState({ defaultSort: { field: 'name', dir: 'desc' }, defaultPageSize: 25 }),
    );
    expect(result.current.page).toBe(0);
    expect(result.current.pageSize).toBe(25);
    expect(result.current.sort).toEqual({ field: 'name', dir: 'desc' });
    expect(result.current.search).toBe('');
    expect(result.current.q).toBe('');
  });

  it('toggleSort flips direction on the same field and resets the page', () => {
    const { result } = renderHook(() =>
      useListPageState({ defaultSort: { field: 'email', dir: 'asc' } }),
    );
    act(() => result.current.setPage(4));

    act(() => result.current.toggleSort('email'));
    expect(result.current.sort).toEqual({ field: 'email', dir: 'desc' });
    expect(result.current.page).toBe(0);
  });

  it('toggleSort switches to asc on a new field and resets the page', () => {
    const { result } = renderHook(() =>
      useListPageState({ defaultSort: { field: 'email', dir: 'desc' } }),
    );
    act(() => result.current.setPage(2));

    act(() => result.current.toggleSort('name'));
    expect(result.current.sort).toEqual({ field: 'name', dir: 'asc' });
    expect(result.current.page).toBe(0);
  });

  it('setPageSize changes the size and resets the page', () => {
    const { result } = renderHook(() =>
      useListPageState({ defaultSort: { field: 'name', dir: 'asc' } }),
    );
    act(() => result.current.setPage(3));

    act(() => result.current.setPageSize(50));
    expect(result.current.pageSize).toBe(50);
    expect(result.current.page).toBe(0);
  });

  it('debounces the search term and resets the page when it lands', () => {
    vi.useFakeTimers();
    try {
      const { result } = renderHook(() =>
        useListPageState({ defaultSort: { field: 'name', dir: 'asc' } }),
      );
      act(() => result.current.setPage(3));

      act(() => result.current.setSearch('ad'));
      expect(result.current.search).toBe('ad');
      expect(result.current.q).toBe(''); // still inside the debounce window

      act(() => {
        vi.advanceTimersByTime(300);
      });
      expect(result.current.q).toBe('ad');
      expect(result.current.page).toBe(0);
    } finally {
      vi.useRealTimers();
    }
  });

  it('initializes pageSize from localStorage when storageKey is provided', () => {
    window.localStorage.setItem('sf_table_prefs_users', JSON.stringify({ pageSize: 50 }));
    const { result } = renderHook(() =>
      useListPageState({
        defaultSort: { field: 'name', dir: 'asc' },
        defaultPageSize: 10,
        storageKey: 'users',
      }),
    );
    expect(result.current.pageSize).toBe(50);
  });

  it('persists pageSize changes to localStorage when storageKey is provided', () => {
    const { result } = renderHook(() =>
      useListPageState({
        defaultSort: { field: 'name', dir: 'asc' },
        defaultPageSize: 10,
        storageKey: 'users',
      }),
    );
    act(() => result.current.setPageSize(100));

    const stored = JSON.parse(window.localStorage.getItem('sf_table_prefs_users') ?? '{}');
    expect(stored.pageSize).toBe(100);
  });

  it('setFilters replaces the clauses and resets the page (K-49)', () => {
    const { result } = renderHook(() =>
      useListPageState({ defaultSort: { field: 'name', dir: 'asc' } }),
    );
    act(() => result.current.setPage(2));

    const clause = { field: 'roleCount', operator: 'GTE' as const, values: ['1'] };
    act(() => result.current.setFilters([clause]));
    expect(result.current.filters).toEqual([clause]);
    expect(result.current.page).toBe(0);

    // Clearing keeps the page-reset contract.
    act(() => result.current.setPage(5));
    act(() => result.current.setFilters([]));
    expect(result.current.filters).toEqual([]);
    expect(result.current.page).toBe(0);
  });

  it('starts with no filter clauses', () => {
    const { result } = renderHook(() =>
      useListPageState({ defaultSort: { field: 'name', dir: 'asc' } }),
    );
    expect(result.current.filters).toEqual([]);
  });
});

describe('useListPageState (syncUrl — K-55)', () => {
  const DEFAULTS = { defaultSort: { field: 'createdDate', dir: 'desc' } as const };

  /** jsdom URL reset + blob helper — every syncUrl test starts from a known URL. */
  const setUrl = (state: SearchQueryState | null, raw?: string) => {
    if (raw !== undefined) {
      window.history.replaceState(null, '', raw);
      return;
    }
    if (state) {
      window.history.replaceState(null, '', `/request-logs?sq=${encodeSearchQuery(state)}`);
    } else {
      window.history.replaceState(null, '', '/request-logs');
    }
  };

  beforeEach(() => {
    window.localStorage.clear();
    window.history.replaceState(null, '', '/request-logs');
  });
  afterEach(() => window.history.replaceState(null, '', '/'));

  it('keeps the URL bare on a fresh mount (no sq written before the first change)', () => {
    setUrl(null);
    renderHook(() => useListPageState({ ...DEFAULTS, syncUrl: true }));
    expect(window.location.search).toBe('');
  });

  it('hydrates the full state from the sq param', () => {
    const state: SearchQueryState = {
      v: 1,
      page: 3,
      size: 25,
      sorts: [{ field: 'status', dir: 'asc' }],
      q: 'ğüş',
      qFields: ['path'],
      filters: [{ field: 'method', operator: 'EQ', values: ['GET'] }],
    };
    setUrl(state);
    const { result } = renderHook(() => useListPageState({ ...DEFAULTS, syncUrl: true }));
    expect(result.current.page).toBe(3);
    expect(result.current.pageSize).toBe(25);
    expect(result.current.sort).toEqual({ field: 'status', dir: 'asc' });
    expect(result.current.search).toBe('ğüş');
    expect(result.current.searchFields).toEqual(['path']);
    expect(result.current.filters).toEqual([{ field: 'method', operator: 'EQ', values: ['GET'] }]);
  });

  it('URL wins over storageKey defaults on mount', () => {
    window.localStorage.setItem('sf_table_prefs_users', JSON.stringify({ pageSize: 50 }));
    setUrl({ v: 1, page: 1, size: 25, sorts: [{ field: 'name', dir: 'asc' }] });
    const { result } = renderHook(() =>
      useListPageState({ ...DEFAULTS, defaultPageSize: 10, storageKey: 'users', syncUrl: true }),
    );
    expect(result.current.pageSize).toBe(25);
  });

  it('falls back to defaults on a broken sq value', () => {
    setUrl(null, '/request-logs?sq=%2B%2Fbroken');
    const { result } = renderHook(() => useListPageState({ ...DEFAULTS, syncUrl: true }));
    expect(result.current.page).toBe(0);
    expect(result.current.sort).toEqual({ field: 'createdDate', dir: 'desc' });
    expect(result.current.filters).toEqual([]);
  });

  it('a committed change pushes the sq param into the URL', () => {
    setUrl(null);
    const { result } = renderHook(() => useListPageState({ ...DEFAULTS, syncUrl: true }));
    act(() => result.current.toggleSort('status'));
    expect(window.location.search).toContain('sq=');

    const restored = decodeParam();
    expect(restored?.sorts[0]).toEqual({ field: 'status', dir: 'asc' });
    expect(restored?.page).toBe(0);
  });

  it('typing updates the URL with replace (no history entries), landing with the debounced q', () => {
    vi.useFakeTimers();
    try {
      setUrl(null);
      const { result } = renderHook(() => useListPageState({ ...DEFAULTS, syncUrl: true }));
      const historyLength = window.history.length;

      act(() => result.current.setSearch('ad'));
      act(() => vi.advanceTimersByTime(300));

      expect(window.location.search).toContain('sq=');
      expect(decodeParam()?.q).toBe('ad');
      expect(window.history.length).toBe(historyLength); // replace, not push
    } finally {
      vi.useRealTimers();
    }
  });

  it('popstate re-hydrates the state from the new URL', () => {
    setUrl({ v: 1, page: 0, size: 10, sorts: [{ field: 'createdDate', dir: 'desc' }] });
    const { result } = renderHook(() => useListPageState({ ...DEFAULTS, syncUrl: true }));

    act(() => result.current.toggleSort('status'));
    expect(decodeParam()?.sorts[0]).toEqual({ field: 'status', dir: 'asc' });

    // Simulate back/forward landing on a different sq URL.
    setUrl({ v: 1, page: 5, size: 50, sorts: [{ field: 'path', dir: 'desc' }], q: 'x' });
    act(() => window.dispatchEvent(new PopStateEvent('popstate')));

    expect(result.current.page).toBe(5);
    expect(result.current.pageSize).toBe(50);
    expect(result.current.sort).toEqual({ field: 'path', dir: 'desc' });
    expect(result.current.search).toBe('x');
  });

  it('popstate to a bare URL restores the defaults', () => {
    setUrl({ v: 1, page: 4, size: 50, sorts: [{ field: 'path', dir: 'desc' }] });
    const { result } = renderHook(() =>
      useListPageState({ ...DEFAULTS, defaultPageSize: 10, syncUrl: true }),
    );
    expect(result.current.page).toBe(4);

    setUrl(null, '/request-logs');
    act(() => window.dispatchEvent(new PopStateEvent('popstate')));

    expect(result.current.page).toBe(0);
    expect(result.current.pageSize).toBe(10);
    expect(result.current.sort).toEqual({ field: 'createdDate', dir: 'desc' });
  });

  it('hydration does not clobber the page when the debounced q lands later (popstate lag guard)', () => {
    vi.useFakeTimers();
    try {
      setUrl({ v: 1, page: 0, size: 10, sorts: [{ field: 'createdDate', dir: 'desc' }], q: 'a' });
      const { result } = renderHook(() => useListPageState({ ...DEFAULTS, syncUrl: true }));

      setUrl({ v: 1, page: 5, size: 10, sorts: [{ field: 'createdDate', dir: 'desc' }], q: 'xyz' });
      act(() => window.dispatchEvent(new PopStateEvent('popstate')));
      expect(result.current.page).toBe(5);

      act(() => vi.advanceTimersByTime(500));
      expect(result.current.search).toBe('xyz');
      expect(result.current.page).toBe(5); // q landing must NOT reset the hydrated page
    } finally {
      vi.useRealTimers();
    }
  });
  it('a filter applied during the search debounce window does not swallow the pending q (regression)', () => {
    vi.useFakeTimers();
    try {
      setUrl(null);
      const { result } = renderHook(() => useListPageState({ ...DEFAULTS, syncUrl: true }));

      act(() => result.current.setSearch('adm'));
      // Filter lands INSIDE the debounce window — a write-intent flag consumed here
      // used to leave the late q out of the URL.
      act(() => result.current.setFilters([{ field: 'status', operator: 'EQ', values: ['500'] }]));
      act(() => vi.advanceTimersByTime(400));

      const restored = decodeParam();
      expect(restored?.q).toBe('adm');
      expect(restored?.filters).toEqual([{ field: 'status', operator: 'EQ', values: ['500'] }]);
    } finally {
      vi.useRealTimers();
    }
  });

  it('successive rapid filter changes all reach the URL (last one wins)', () => {
    setUrl(null);
    const { result } = renderHook(() => useListPageState({ ...DEFAULTS, syncUrl: true }));
    act(() => result.current.setFilters([{ field: 'method', operator: 'EQ', values: ['GET'] }]));
    act(() => result.current.setFilters([{ field: 'status', operator: 'GTE', values: ['400'] }]));
    expect(decodeParam()?.filters).toEqual([{ field: 'status', operator: 'GTE', values: ['400'] }]);
  });
  it('rapid-successive sort changes keep the chain coherent (multi-sort, K-55 F6)', () => {
    const { result } = renderHook(() => useListPageState({ ...DEFAULTS, syncUrl: true }));

    act(() => result.current.toggleSort('status', true));
    act(() => result.current.toggleSort('durationMs', true));
    expect(result.current.sorts).toEqual([
      { field: 'status', dir: 'asc' },
      { field: 'durationMs', dir: 'asc' },
    ]);

    // Shift+click again on status: asc → desc within the chain.
    act(() => result.current.toggleSort('status', true));
    expect(result.current.sorts).toEqual([
      { field: 'status', dir: 'desc' },
      { field: 'durationMs', dir: 'asc' },
    ]);

    // Third additive click removes status from the chain.
    act(() => result.current.toggleSort('status', true));
    expect(result.current.sorts).toEqual([{ field: 'durationMs', dir: 'asc' }]);

    // Plain click replaces the whole chain.
    act(() => result.current.toggleSort('traceId'));
    expect(result.current.sorts).toEqual([{ field: 'traceId', dir: 'asc' }]);
    expect(result.current.sort).toEqual({ field: 'traceId', dir: 'asc' });
    expect(decodeParam()?.sorts).toEqual([{ field: 'traceId', dir: 'asc' }]);
  });
  it('applySearchQuery adopts a saved view as a committed change without clobbering the page (K-55 F7)', () => {
    vi.useFakeTimers();
    try {
      setUrl(null);
      const { result } = renderHook(() => useListPageState({ ...DEFAULTS, syncUrl: true }));

      act(() => result.current.applySearchQuery({
        v: 1,
        page: 4,
        size: 50,
        sorts: [{ field: 'status', dir: 'desc' }],
        q: '_saved',
        filters: [{ field: 'method', operator: 'EQ', values: ['GET'] }],
      }));

      expect(result.current.page).toBe(4);
      expect(result.current.pageSize).toBe(50);
      expect(result.current.sorts).toEqual([{ field: 'status', dir: 'desc' }]);
      expect(result.current.search).toBe('_saved');
      expect(result.current.filters).toEqual([{ field: 'method', operator: 'EQ', values: ['GET'] }]);

      // The debounced q landing must NOT reset the applied page.
      act(() => vi.advanceTimersByTime(400));
      expect(result.current.page).toBe(4);

      // Applying is a committed change — the URL carries it.
      expect(decodeParam()?.page).toBe(4);
      expect(decodeParam()?.q).toBe('_saved');
    } finally {
      vi.useRealTimers();
    }
  });
});

/** Decodes the current window.location.search `sq` param (test helper). */
function decodeParam() {
  const blob = new URLSearchParams(window.location.search).get('sq');
  return blob ? decodeSearchQuery(blob) : null;
}
