import type { ReactNode } from 'react';
import { LuX } from 'react-icons/lu';
import { cn } from '../../lib/cn';
import { useT } from '../../lib/i18n';
import { useDialogPanel } from '../../lib/useDialogPanel';

interface DrawerProps {
  open: boolean;
  title: string;
  onClose: () => void;
  children: ReactNode;
  /** Panel width on md+ screens; the drawer is full-width below md. */
  size?: 'md' | 'lg';
}

const SIZES = {
  md: 'max-w-lg',
  lg: 'max-w-2xl',
};

/**
 * Right-anchored slide-over dialog (K-55 F3) — the table row-detail surface.
 * Same dialog contract as Modal (role/aria-modal, focus trap, Escape + backdrop
 * close, opener focus restore) with the panel docked to the viewport's right edge.
 */
export function Drawer({ open, title, onClose, children, size = 'md' }: DrawerProps) {
  const { t } = useT();
  const { titleId, panelRef } = useDialogPanel(open, onClose);

  if (!open) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex justify-end bg-black/60 backdrop-blur-sm"
      onMouseDown={onClose}
    >
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        className={cn(
          'flex h-full w-full flex-col border-l border-glass bg-sidebar shadow-2xl shadow-black/50 focus:outline-none',
          SIZES[size],
        )}
        onMouseDown={(e) => e.stopPropagation()}
      >
        <header className="flex items-center justify-between border-b border-glass px-6 py-4">
          <h2 id={titleId} className="font-display text-lg font-semibold text-main">{title}</h2>
          <button
            onClick={onClose}
            aria-label={t('common.close')}
            className="rounded-md p-1 text-muted transition-colors hover:bg-main/5 hover:text-main focus:outline-none focus-visible:ring-2 focus-visible:ring-accent/60"
          >
            <LuX size={18} aria-hidden />
          </button>
        </header>
        <div className="flex-1 overflow-y-auto px-6 py-5">{children}</div>
      </div>
    </div>
  );
}
