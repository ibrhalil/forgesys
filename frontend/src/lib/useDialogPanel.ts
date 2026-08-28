import { useEffect, useId, useRef } from 'react';

const FOCUSABLE =
  'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

/**
 * Shared dialog-panel behaviour for Modal and Drawer (K-55 F3): `role="dialog"`
 * focus management — on open the focus moves onto the panel and is trapped
 * (Tab/Shift+Tab cycle inside); Escape closes; on close focus is restored to the
 * opener. Body scroll locks while open. The close effect must key on [open] only —
 * a changing onClose identity (inline closures) would otherwise re-run it and
 * steal focus from inputs on each keystroke.
 */
export function useDialogPanel(open: boolean, onClose: () => void) {
  const titleId = useId();
  const panelRef = useRef<HTMLDivElement>(null);
  const openerRef = useRef<Element | null>(null);
  const onCloseRef = useRef(onClose);
  useEffect(() => {
    onCloseRef.current = onClose;
  });

  useEffect(() => {
    if (!open) return;
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

  return { titleId, panelRef };
}
