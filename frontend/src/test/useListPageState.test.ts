import { describe, expect, it, vi } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useListPageState } from '../lib/useListPageState';

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
});
