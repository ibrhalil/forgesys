import type { ReactNode } from 'react';
import { cn } from '../../lib/cn';
import { Badge } from '../ui/Badge';
import { useT } from '../../lib/i18n';

interface DetailPanelProps {
  title: string;
  children: ReactNode;
  className?: string;
}

/**
 * Titled card used by the IAM detail pages to group related information. Editing
 * surfaces rendered inside a panel put their Save/Cancel actions bottom-right of
 * the panel content (same rule as modal footers) — never in the panel header.
 */
export function DetailPanel({ title, children, className }: DetailPanelProps) {
  return (
    <section className={cn('rounded-lg border border-glass bg-surface p-5', className)}>
      <header className="mb-4">
        <h2 className="m-0 text-xs font-semibold uppercase tracking-wide text-muted">{title}</h2>
      </header>
      {children}
    </section>
  );
}

/** A label + value row in a definition-style grid. */
export function DetailField({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex flex-col gap-0.5">
      <dt className="text-xs uppercase tracking-wide text-muted/70">{label}</dt>
      <dd className="m-0 text-sm text-main">{children ?? <span className="text-muted">—</span>}</dd>
    </div>
  );
}

/** Renders a list of permission-name strings as badges, grouped by module prefix. */
export function PermissionBadges({ permissions }: { permissions: string[] }) {
  const { t } = useT();
  if (permissions.length === 0) {
    return <p className="text-sm text-muted">{t('common.noPermissions')}</p>;
  }
  return (
    <div className="flex flex-wrap gap-1.5">
      {permissions.map((p) => {
        const tone = p.startsWith('iam:') ? 'accent' : p.startsWith('platform:') ? 'warning' : 'blue';
        return <Badge key={p} tone={tone}><span className="font-mono">{p}</span></Badge>;
      })}
    </div>
  );
}
