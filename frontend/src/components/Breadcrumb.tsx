import { Link } from 'react-router-dom';
import { LuChevronRight } from 'react-icons/lu';

export interface Crumb {
  label: string;
  /** Route for clickable segments; omit for plain segments (sections, current page). */
  to?: string;
}

/**
 * Path line shown in the page head, e.g. `Identity & Access > Groups > Developer`.
 * Segments with a `to` render as links; the last segment is highlighted as the
 * current location. Always a single line (truncating segments) so it fits the
 * fixed-height shell topbar.
 */
export function Breadcrumb({ items }: { items: Crumb[] }) {
  return (
    <nav aria-label="breadcrumb">
      <ol className="m-0 flex list-none items-center gap-1.5 p-0 text-sm">
        {items.map((crumb, index) => {
          const isLast = index === items.length - 1;
          return (
            <li key={`${index}-${crumb.label}`} className="flex min-w-0 items-center gap-1.5">
              {index > 0 && <LuChevronRight aria-hidden className="h-3.5 w-3.5 shrink-0 text-muted/50" />}
              {crumb.to && !isLast ? (
                <Link to={crumb.to} className="truncate text-muted transition-colors hover:text-accent">
                  {crumb.label}
                </Link>
              ) : (
                <span
                  className={isLast ? 'truncate font-medium text-accent' : 'truncate text-muted'}
                  aria-current={isLast ? 'page' : undefined}
                >
                  {crumb.label}
                </span>
              )}
            </li>
          );
        })}
      </ol>
    </nav>
  );
}
