import { decodeSearchQuery } from '../lib/searchQuery';
import type { SearchQueryState } from '../lib/searchQuery';

/** Decodes the `sq` param from a recorded fetch URL (K-55 wire-flip test helper). */
export function decodedSq(url: string): SearchQueryState | null {
  const match = url.match(/[?&]sq=([A-Za-z0-9_-]+)/);
  return match ? decodeSearchQuery(match[1]) : null;
}
