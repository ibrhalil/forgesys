import type { ReactNode } from 'react';
import { cn } from '../../../lib/cn';

interface AuthCardProps {
  /** Card width: `sm` for single-action screens, `md` for the register form. */
  size?: 'sm' | 'md';
  /** Optional header tile (logo or status icon) above the title. */
  icon?: ReactNode;
  /** Optional title/subtitle; VerifyTenant renders its own per-state headers instead. */
  title?: string;
  subtitle?: string;
  children: ReactNode;
}

/**
 * Shared layout shell for the auth screens (Login/Register/VerifyTenant): a centered
 * full-screen wrapper over the frosted card, with an optional centered header block.
 * Layout only — no form, validation or navigation logic lives here.
 */
export function AuthCard({ size = 'sm', icon, title, subtitle, children }: AuthCardProps) {
  return (
    <div className="flex min-h-screen items-center justify-center p-4">
      <div
        className={cn(
          'w-full rounded-lg border border-glass bg-sidebar/90 p-8 shadow-2xl shadow-black/50 backdrop-blur-md',
          size === 'md' ? 'max-w-md' : 'max-w-sm',
        )}
      >
        {(icon || title) && (
          <div className="mb-8 flex flex-col items-center gap-3 text-center">
            {icon}
            <div>
              <h1 className="text-2xl font-semibold text-main">{title}</h1>
              {subtitle && <p className="mt-1 text-sm text-muted">{subtitle}</p>}
            </div>
          </div>
        )}
        {children}
      </div>
    </div>
  );
}
