import { decodeSearchQuery } from '../lib/searchQuery';
import type { SearchQueryState } from '../lib/searchQuery';

/** Decodes the `sq` param from a recorded fetch URL (K-55 wire test helper). */
export function decodedSq(url: string): SearchQueryState | null {
  const match = url.match(/[?&]sq=([A-Za-z0-9_-]+)/);
  return match ? decodeSearchQuery(match[1]) : null;
}

/** Reads the flat list params (page/size/sort) off a recorded fetch URL (K-55). */
export function flatParams(url: string): URLSearchParams {
  return new URLSearchParams(url.split('?')[1] ?? '');
}
