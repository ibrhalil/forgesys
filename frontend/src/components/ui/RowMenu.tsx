import { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import type { IconType } from 'react-icons';
import { LuSettings } from 'react-icons/lu';
import { cn } from '../../lib/cn';

/** One entry of a {@link RowMenu}. `danger` renders the item in the destructive tone. */
export interface RowMenuItem {
  label: string;
  onClick: () => void;
  icon?: IconType;
  danger?: boolean;
}

interface RowMenuProps {
  /** Visible items — callers filter by permission before passing. Renders nothing when empty. */
  items: RowMenuItem[];
  /** Accessible name for the trigger (usually the actions column header). */
  ariaLabel: string;
  /** Trigger icon — gear in tables, ellipsis-vertical as a page-head overflow menu. */
  icon?: IconType;
}

const MENU_WIDTH = 192; // w-48
const ITEM_HEIGHT = 36;

/**
 * Row-actions menu: a single compact trigger (gear in DataTable rows, ellipsis
 * via the `icon` prop as a page-head overflow menu) so surfaces never widen or
 * crowd with buttons; the menu renders in a fixed-position portal (like
 * SelectInput's menu) so container overflow rules cannot clip it. Callers pass
 * only the actions the current user is authorized to see — an empty array
 * renders no trigger. Destructive actions belong inside the menu (danger tone),
 * never as top-level buttons. Icons come from `react-icons` (Lucide set — same
 * stroke style as the AppShell nav).
 */
export function RowMenu({ items, ariaLabel, icon: Icon = LuSettings }: RowMenuProps) {
  const [open, setOpen] = useState(false);
  const [top, setTop] = useState(0);
  const [left, setLeft] = useState(0);
  const triggerRef = useRef<HTMLButtonElement>(null);

  const estimatedHeight = items.length * ITEM_HEIGHT + 8;

  const place = () => {
    const rect = triggerRef.current?.getBoundingClientRect();
    if (!rect) return;
    const openUp = rect.bottom + estimatedHeight + 12 > window.innerHeight;
    setTop(openUp ? Math.max(8, rect.top - estimatedHeight - 6) : rect.bottom + 6);
    setLeft(Math.max(8, Math.min(rect.right - MENU_WIDTH, window.innerWidth - MENU_WIDTH - 8)));
  };

  useEffect(() => {
    if (!open) return;
    const onPointerDown = (e: PointerEvent) => {
      const target = e.target as Node;
      if (!triggerRef.current?.contains(target) && !(e.target as HTMLElement).closest('[role="menu"]')) {
        setOpen(false);
      }
    };
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        setOpen(false);
        triggerRef.current?.focus();
      }
    };
    // Any scroll (including the table's own overflow-x container) closes the menu —
    // simpler and more predictable than repositioning a fixed portal.
    const close = () => setOpen(false);
    document.addEventListener('pointerdown', onPointerDown, true);
    document.addEventListener('keydown', onKeyDown);
    window.addEventListener('scroll', close, true);
    window.addEventListener('resize', close);
    return () => {
      document.removeEventListener('pointerdown', onPointerDown, true);
      document.removeEventListener('keydown', onKeyDown);
      window.removeEventListener('scroll', close, true);
      window.removeEventListener('resize', close);
    };
  }, [open]);

  if (items.length === 0) return null;

  return (
    <>
      <button
        ref={triggerRef}
        type="button"
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label={ariaLabel}
        title={ariaLabel}
        onClick={() => {
          if (!open) place();
          setOpen((v) => !v);
        }}
        className={cn(
          'inline-flex h-7 w-7 items-center justify-center rounded-md text-muted transition-colors hover:bg-main/5 hover:text-main focus:outline-none focus-visible:ring-2 focus-visible:ring-accent/60',
          open && 'bg-main/5 text-main',
        )}
      >
        <Icon aria-hidden className="h-4 w-4" />
      </button>

      {open &&
        createPortal(
          <div
            role="menu"
            aria-label={ariaLabel}
            style={{ position: 'fixed', top, left, width: MENU_WIDTH, zIndex: 60 }}
            className="overflow-hidden rounded-lg border border-glass bg-surface py-1 shadow-xl shadow-black/15"
          >
            {items.map((item) => (
              <button
                key={item.label}
                type="button"
                role="menuitem"
                onClick={() => {
                  setOpen(false);
                  item.onClick();
                }}
                className={cn(
                  'flex w-full items-center gap-2.5 px-3 py-2 text-left text-sm transition-colors focus:outline-none',
                  item.danger
                    ? 'font-medium text-danger hover:bg-danger/10'
                    : 'text-main hover:bg-accent/5 hover:text-accent',
                )}
              >
                {item.icon && (
                  <item.icon
                    aria-hidden
                    className={cn('h-4 w-4 shrink-0', !item.danger && 'opacity-70')}
                  />
                )}
                <span className="truncate">{item.label}</span>
              </button>
            ))}
          </div>,
          document.body,
        )}
    </>
  );
}
