import { useMemo, useState } from 'react';
import { Modal } from '../ui/Modal';
import { Button } from '../ui/Button';
import { SearchInput } from '../ui/SearchInput';
import { EmptyState } from '../ui/EmptyState';
import { Spinner } from '../ui/Spinner';
import { useT } from '../../lib/i18n';

interface PermissionListModalProps {
  open: boolean;
  onClose: () => void;
  /** Modal title — caller-scoped, e.g. "Effective permissions — developer@acme.com". */
  title: string;
  /** Permission names (`module:resource:action`); undefined while loading. */
  permissions: string[] | undefined;
}

interface PermissionGroup {
  module: string;
  names: string[];
}

/**
 * Effective-permissions viewer: a searchable modal over the permission list instead
 * of the old wall-of-badges inline panel. Permissions are grouped by their module
 * segment (`iam:` / `pm:` / …) with per-group counts; the search box filters
 * client-side (substring on the full name) and hides non-matching groups. Footer is
 * a single Close button bottom-right, per the action-footer rule.
 */
export function PermissionListModal({ open, onClose, title, permissions }: PermissionListModalProps) {
  const { t } = useT();
  const [query, setQuery] = useState('');

  const groups: PermissionGroup[] = useMemo(() => {
    if (!permissions) return [];
    const q = query.trim().toLowerCase();
    const byModule = new Map<string, string[]>();
    for (const name of permissions) {
      if (q && !name.toLowerCase().includes(q)) continue;
      const module = name.split(':', 1)[0] ?? name;
      const list = byModule.get(module);
      if (list) list.push(name);
      else byModule.set(module, [name]);
    }
    return Array.from(byModule.entries())
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([module, names]) => ({ module, names: names.sort() }));
  }, [permissions, query]);

  return (
    <Modal
      open={open}
      title={title}
      onClose={onClose}
      footer={
        <Button variant="ghost" onClick={onClose}>{t('common.close')}</Button>
      }
    >
      <div className="flex flex-col gap-4">
        <SearchInput value={query} onChange={setQuery} placeholder={t('permissions.searchPh')} />

        {permissions === undefined ? (
          <div className="flex items-center justify-center py-16">
            <Spinner className="border-muted/40 border-t-accent" />
          </div>
        ) : groups.length === 0 ? (
          <EmptyState message={t('permissions.emptyFiltered')} />
        ) : (
          <div className="flex flex-col gap-5">
            {groups.map((group) => (
              <section key={group.module}>
                <h3 className="m-0 mb-2 flex items-baseline gap-2 text-xs font-semibold uppercase tracking-wide text-muted">
                  <span className="font-mono normal-case tracking-normal">{group.module}</span>
                  <span className="text-muted/60">({group.names.length})</span>
                </h3>
                <ul className="m-0 flex list-none flex-col gap-1 p-0">
                  {group.names.map((name) => (
                    <li
                      key={name}
                      className="rounded-md bg-main/5 px-3 py-1.5 font-mono text-sm text-main"
                    >
                      {name}
                    </li>
                  ))}
                </ul>
              </section>
            ))}
          </div>
        )}
      </div>
    </Modal>
  );
}
