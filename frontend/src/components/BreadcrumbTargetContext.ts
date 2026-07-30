import { createContext, useContext } from 'react';

/**
 * Portal target for the page breadcrumb — the AppShell's fixed topbar. `Page`
 * portals its breadcrumb here while the shell is mounted, so the path line stays
 * visible while the page body scrolls underneath. `null` when no shell is present
 * (Page then renders the breadcrumb inline as a fallback).
 */
export const BreadcrumbTargetContext = createContext<HTMLElement | null>(null);

export function useBreadcrumbTarget(): HTMLElement | null {
  return useContext(BreadcrumbTargetContext);
}
