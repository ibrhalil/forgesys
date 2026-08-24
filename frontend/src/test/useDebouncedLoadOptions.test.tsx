import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { renderHook } from '@testing-library/react';
import { useDebouncedLoadOptions } from '../components/pickers/useDebouncedLoadOptions';
import type { SelectOption } from '../lib/select';

/** Unit tests for the picker debounce: collapse + stale guard + empty fast path. */
describe('useDebouncedLoadOptions', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it('collapses rapid keystrokes into the last input only', async () => {
    const fetcher = vi.fn<(input: string) => Promise<SelectOption<string>[]>>(
      async (input) => [{ value: input, label: input }],
    );
    const { result } = renderHook(() => useDebouncedLoadOptions(fetcher, 300));

    result.current('a');
    result.current('ab');
    await vi.advanceTimersByTimeAsync(300);

    expect(fetcher).toHaveBeenCalledTimes(1);
    expect(fetcher).toHaveBeenCalledWith('ab');
  });

  it('resolves stale in-flight fetches to [] (latest input wins)', async () => {
    let resolveFirst!: (opts: SelectOption<string>[]) => void;
    const fetcher = vi.fn<(input: string) => Promise<SelectOption<string>[]>>((input) =>
      input === 'a'
        ? new Promise((res) => {
            resolveFirst = res;
          })
        : Promise.resolve([{ value: 'ab', label: 'ab' }]),
    );
    const { result } = renderHook(() => useDebouncedLoadOptions(fetcher, 0));

    const p1 = result.current('a');
    const p2 = result.current('ab');

    expect(await p2).toEqual([{ value: 'ab', label: 'ab' }]);
    resolveFirst([{ value: 'a', label: 'a' }]);
    expect(await p1).toEqual([]);
  });

  it('fetches immediately for an empty input (menu first open) regardless of the window', async () => {
    const fetcher = vi.fn<(input: string) => Promise<SelectOption<string>[]>>(
      async (input) => [{ value: input, label: input }],
    );
    const { result } = renderHook(() => useDebouncedLoadOptions(fetcher, 300));

    const p = result.current('');
    // No timer advance — the empty input bypasses the debounce window.
    expect(fetcher).toHaveBeenCalledWith('');
    expect(await p).toEqual([{ value: '', label: '' }]);
  });

  it('clears the pending timer on unmount', async () => {
    const fetcher = vi.fn<(input: string) => Promise<SelectOption<string>[]>>(
      async (input) => [{ value: input, label: input }],
    );
    const { result, unmount } = renderHook(() => useDebouncedLoadOptions(fetcher, 300));

    result.current('a');
    unmount();
    await vi.advanceTimersByTimeAsync(300);

    expect(fetcher).not.toHaveBeenCalled();
  });
});
