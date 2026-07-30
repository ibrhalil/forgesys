import { useEffect, useState } from 'react';

/**
 * Returns `value`, but only updating after it has stayed unchanged for `delayMs`.
 * Keeps server-side list search from firing a request per keystroke — the input
 * renders the raw value while queries key on the debounced one.
 */
export function useDebouncedValue<T>(value: T, delayMs = 300): T {
  const [debounced, setDebounced] = useState(value);

  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delayMs);
    return () => clearTimeout(timer);
  }, [value, delayMs]);

  return debounced;
}
