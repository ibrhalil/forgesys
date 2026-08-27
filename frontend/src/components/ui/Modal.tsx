import { useEffect, useId, useRef, type ReactNode } from 'react';
import { LuX } from 'react-icons/lu';
import { cn } from '../../lib/cn';
import { useT } from '../../lib/i18n';

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

const FOCUSABLE =
  'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

/**
 * Accessible dialog: `role="dialog"` + `aria-modal`, labelled by the title. On open
 * the focus moves into the panel and is trapped (Tab/Shift+Tab cycle inside); on
 * close it is restored to the opener. Escape and backdrop mousedown close as before.
 */
export function Modal({ open, title, onClose, children, footer, size = 'md', describedby }: ModalProps) {
  const titleId = useId();
  const panelRef = useRef<HTMLDivElement>(null);
  const openerRef = useRef<Element | null>(null);
  // Latest-ref: the focus effect must depend on [open] only — a changing onClose
  // identity (inline closures) would re-run it and steal focus from inputs on each keystroke.
  const onCloseRef = useRef(onClose);
  useEffect(() => {
    onCloseRef.current = onClose;
  });
  const { t } = useT();

  useEffect(() => {
    if (!open) return;
    // Remember the opener so focus can return when the dialog closes.
    openerRef.current = document.activeElement;
    const panel = panelRef.current;
    // Focus the dialog surface itself (not the first control) so opening never
    // lands on the close button; the focus trap then walks the controls.
    panel?.focus({ preventScroll: true });

    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        onCloseRef.current();
        return;
      }
      if (e.key !== 'Tab') return;
      // Focus trap: cycle Tab/Shift+Tab within the dialog panel.
      const focusables = Array.from(panel?.querySelectorAll<HTMLElement>(FOCUSABLE) ?? []);
      if (focusables.length === 0) {
        e.preventDefault();
        panel?.focus();
        return;
      }
      const firstEl = focusables[0];
      const lastEl = focusables[focusables.length - 1];
      const active = document.activeElement;
      if (e.shiftKey && (active === firstEl || active === panel)) {
        e.preventDefault();
        lastEl.focus();
      } else if (!e.shiftKey && active === lastEl) {
        e.preventDefault();
        firstEl.focus();
      } else if (!panel?.contains(active)) {
        e.preventDefault();
        firstEl.focus();
      }
    };
    document.addEventListener('keydown', onKey);
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', onKey);
      document.body.style.overflow = '';
      (openerRef.current as HTMLElement | null)?.focus?.();
    };
  }, [open]);

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
          'w-full rounded-2xl border border-glass bg-sidebar shadow-2xl shadow-black/50',
          'flex max-h-[90vh] flex-col focus:outline-none',
          SIZES[size],
        )}
        onMouseDown={(e) => e.stopPropagation()}
      >
        <header className="flex items-center justify-between border-b border-glass px-6 py-4">
          <h2 id={titleId} className="text-lg font-semibold text-main">{title}</h2>
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
