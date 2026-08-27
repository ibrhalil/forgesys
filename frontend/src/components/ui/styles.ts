/**
 * K-54 UI design contract class strings — the single source for input, label,
 * meta and popover recipes (see frontend/AGENTS.md "UI design contracts").
 * Consume these; never re-inline the recipes per component.
 */

/** Form input base (md, ~38px): fill, border, focus ring per the interaction ramp. */
export const INPUT_BASE =
  'w-full rounded-md border bg-main/5 px-3 py-2 text-sm text-main placeholder:text-muted/50 ' +
  'transition-colors focus:outline-none focus:ring-2 focus:ring-accent/50';

/** Compact input (sm, ~32px) for inline controls (filters, table inline editors). */
export const INPUT_BASE_SM =
  'w-full rounded border border-glass bg-main/5 px-2.5 py-1.5 text-[13px] text-main placeholder:text-muted/50 ' +
  'transition-colors focus:outline-none focus:ring-2 focus:ring-accent/50';


/** Micro-label (Field labels, table column heads) — the ONLY uppercase voice. */
export const MICRO_LABEL = 'text-[11px] font-semibold uppercase tracking-wider text-muted';

/** Machine metadata (ids, timestamps, counters) — semantic mono, never decoration. */
export const META_MONO = 'font-mono text-xs tabular-nums text-muted';

/** Floating menu surface (option/action lists): hairline + crisp shadow, z-60 portal. */
export const POPOVER_MENU = 'overflow-hidden rounded-lg border border-glass bg-surface shadow-lg shadow-black/10';

/** Panel popover surface (filter/settings panels): same recipe as menus (K-54 merge). */
export const POPOVER_PANEL = 'rounded-lg border border-glass bg-surface shadow-lg shadow-black/10';
