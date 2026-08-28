import type { ReactNode } from 'react';
import { LuX } from 'react-icons/lu';
import { cn } from '../../lib/cn';
import { useT } from '../../lib/i18n';
import { useDialogPanel } from '../../lib/useDialogPanel';

interface ModalProps {
  open: boolean;
  title: string;
  onClose: () => void;
  children: ReactNode;
  footer?: ReactNode;
  /** Modal width; defaults to md. */
  size?: 'sm' | 'md' | 'lg';
  /** id of the element describing the dialog, wired to aria-describedby. */
  describedby?: string;
}

const SIZES = {
  sm: 'max-w-sm',
  md: 'max-w-lg',
  lg: 'max-w-2xl',
};

/**
 * Accessible dialog: `role="dialog"` + `aria-modal`, labelled by the title. Focus
 * trap, Escape/backdrop close and opener-restore live in {@link useDialogPanel}
 * (shared with Drawer).
 */
export function Modal({ open, title, onClose, children, footer, size = 'md', describedby }: ModalProps) {
  const { titleId, panelRef } = useDialogPanel(open, onClose);
  const { t } = useT();

  if (!open) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-sm"
      onMouseDown={onClose}
    >
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={describedby}
        tabIndex={-1}
        className={cn(
          'w-full rounded-lg border border-glass bg-sidebar shadow-2xl shadow-black/50',
          'flex max-h-[90vh] flex-col focus:outline-none',
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
        {footer && (
          <footer className="flex items-center justify-end gap-3 border-t border-glass px-6 py-4">
            {footer}
          </footer>
        )}
      </div>
    </div>
  );
}
