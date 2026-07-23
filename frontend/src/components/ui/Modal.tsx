import { useEffect, type ReactNode } from 'react';
import { cn } from '../../lib/cn';

interface ModalProps {
  open: boolean;
  title: string;
  onClose: () => void;
  children: ReactNode;
  footer?: ReactNode;
  /** Modal width; defaults to md. */
  size?: 'sm' | 'md' | 'lg';
}

const SIZES = {
  sm: 'max-w-sm',
  md: 'max-w-lg',
  lg: 'max-w-2xl',
};

export function Modal({ open, title, onClose, children, footer, size = 'md' }: ModalProps) {
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', onKey);
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', onKey);
      document.body.style.overflow = '';
    };
  }, [open, onClose]);

  if (!open) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-sm"
      onMouseDown={onClose}
    >
      <div
        className={cn(
          'w-full rounded-2xl border border-glass bg-sidebar shadow-2xl shadow-black/50',
          'flex max-h-[90vh] flex-col',
          SIZES[size],
        )}
        onMouseDown={(e) => e.stopPropagation()}
      >
        <header className="flex items-center justify-between border-b border-glass px-6 py-4">
          <h2 className="text-lg font-semibold text-main">{title}</h2>
          <button
            onClick={onClose}
            className="rounded-md p-1 text-muted transition-colors hover:bg-white/5 hover:text-main"
            aria-label="Close"
          >
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M18 6 6 18M6 6l12 12" />
            </svg>
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
