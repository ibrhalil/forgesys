import { useCallback, useEffect, useRef } from 'react';
import type { SelectOption } from '../../lib/select';

/**
 * Debounced `loadOptions` for react-select async pickers: collapses rapid
 * keystrokes into one fetch per debounce window and drops stale responses —
 * only the latest input resolves with its results, earlier in-flight calls
 * resolve to `[]`. An empty input skips the debounce window entirely so the
 * menu's first open loads instantly. `delayMs <= 0` disables debouncing
 * (tests).
 */
export function useDebouncedLoadOptions<V>(
  fetcher: (input: string) => Promise<SelectOption<V>[]>,
  delayMs = 300,
): (input: string) => Promise<SelectOption<V>[]> {
  const latest = useRef('');
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const fetcherRef = useRef(fetcher);

  useEffect(() => {
    fetcherRef.current = fetcher;
  }, [fetcher]);

  // Never leave a timer firing after unmount.
  useEffect(
    () => () => {
      if (timer.current) clearTimeout(timer.current);
    },
    [],
  );

  return useCallback(
    (input: string) =>
      new Promise<SelectOption<V>[]>((resolve) => {
        latest.current = input;
        const run = () =>
          fetcherRef
            .current(input)
            .then((options) => resolve(latest.current === input ? options : []))
            .catch(() => resolve([]));
        if (timer.current) clearTimeout(timer.current);
        // Empty input = menu first open — load now; otherwise debounce (or skip
        // the window entirely when delayMs <= 0).
        if (input === '' || delayMs <= 0) {
          run();
          return;
        }
        timer.current = setTimeout(() => {
          timer.current = null;
          run();
        }, delayMs);
      }),
    [delayMs],
  );
}
