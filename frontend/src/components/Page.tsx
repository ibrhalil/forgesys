import { createPortal } from 'react-dom';
import type { ReactNode } from 'react';
import { Breadcrumb, type Crumb } from './Breadcrumb';
import { useBreadcrumbTarget } from './BreadcrumbTargetContext';

interface PageProps {
  breadcrumb?: Crumb[];
  title: ReactNode;
  description?: ReactNode;
  /** Right-aligned head actions (create button, edit/delete…). */
  actions?: ReactNode;
  children: ReactNode;
}

/**
 * Standard page scaffold: a head (title/description + actions) over a body. Every
 * routed screen renders through this so head/body structure and spacing stay
 * uniform across the app.
 *
 * The breadcrumb portals into the AppShell's fixed topbar (outside the scroll
 * container) so it never scrolls away — and future sticky elements like table
 * headers can use plain `top-0` inside the scroll container without coordinating
 * offsets with the breadcrumb. Without a shell it falls back to inline rendering
 * above the head.
 */
export function Page({ breadcrumb, title, description, actions, children }: PageProps) {
  const target = useBreadcrumbTarget();
  const crumbs = breadcrumb && breadcrumb.length > 0 ? <Breadcrumb items={breadcrumb} /> : null;

  return (
    <div className="flex flex-col gap-6">
      {crumbs && (target ? createPortal(crumbs, target) : crumbs)}
      <header className="flex flex-col gap-3">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex min-w-0 flex-col gap-1">
            <h1 className="m-0 text-2xl font-semibold tracking-tight text-main">{title}</h1>
            {description != null && <p className="m-0 text-sm text-muted">{description}</p>}
          </div>
          {actions && <div className="flex shrink-0 flex-wrap items-center gap-2">{actions}</div>}
        </div>
      </header>
      {children}
    </div>
  );
}
