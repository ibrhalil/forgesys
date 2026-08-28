import { useEffect, useRef, useState } from 'react';
import { LuBookmark, LuTrash2 } from 'react-icons/lu';
import { cn } from '../../lib/cn';
import { useT } from '../../lib/i18n';
import { deleteSavedView, listSavedViews, saveSavedView, type SavedView } from '../../lib/savedViews';
import type { SearchQueryState } from '../../lib/searchQuery';

interface SavedViewsMenuProps {
  /** localStorage scope — the table's storageKey. */
  storageKey: string;
  /** The current committed query state (persisted when saving). */
  state: SearchQueryState;
  /** Applies a saved view — the page feeds it to `useListPageState.applySearchQuery`. */
  onApply: (state: SearchQueryState) => void;
}

/**
 * Named-views dropdown for list pages (K-55 F7, localStorage v1): save the current
 * filters/sort/page under a name, re-apply in one click, delete stale ones. The
 * stored payload equals the URL `sq` blob — applying a view IS opening a shared link.
 */
export function SavedViewsMenu({ storageKey, state, onApply }: SavedViewsMenuProps) {
  const { t } = useT();
  const [open, setOpen] = useState(false);
  const [views, setViews] = useState<SavedView[]>(() => listSavedViews(storageKey));
  const [name, setName] = useState('');
  const menuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const onPointerDown = (e: MouseEvent | TouchEvent) => {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) setOpen(false);
    };
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false);
    };
    document.addEventListener('mousedown', onPointerDown);
    document.addEventListener('touchstart', onPointerDown);
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('mousedown', onPointerDown);
      document.removeEventListener('touchstart', onPointerDown);
      document.removeEventListener('keydown', onKeyDown);
    };
  }, [open]);

  const refresh = () => setViews(listSavedViews(storageKey));

  const save = () => {
    const saved = saveSavedView(storageKey, name, state);
    if (saved) {
      setName('');
      refresh();
    }
  };

  return (
    <div className="relative" ref={menuRef}>
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-expanded={open}
        className={cn(
          'inline-flex h-8 items-center gap-1.5 rounded-lg border border-glass bg-surface px-2.5 text-xs text-muted transition-colors',
          'hover:border-accent/40 hover:bg-accent/5 hover:text-main focus:outline-none focus-visible:ring-2 focus-visible:ring-accent/60',
          open && 'border-accent/50 bg-accent/5 text-accent',
        )}
        title={t('savedViews.title')}
      >
        <LuBookmark className="h-3.5 w-3.5" aria-hidden />
        <span className="hidden sm:inline">{t('savedViews.title')}</span>
        {views.length > 0 && (
          <span className="rounded bg-accent/15 px-1 text-[10px] font-semibold text-accent">{views.length}</span>
        )}
      </button>

      {open && (
        <div className="absolute left-0 top-full z-60 mt-1.5 w-64 overflow-hidden rounded-lg border border-glass bg-surface shadow-lg shadow-black/10">
          <div className="flex gap-1.5 border-b border-glass bg-bg/30 p-2">
            <input
              value={name}
              onChange={(e) => setName(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && save()}
              placeholder={t('savedViews.namePh')}
              aria-label={t('savedViews.namePh')}
              className="h-7 w-full min-w-0 rounded-md border border-glass bg-surface px-2 text-xs text-main focus:outline-none focus-visible:ring-2 focus-visible:ring-accent/50"
            />
            <button
              type="button"
              onClick={save}
              disabled={!name.trim()}
              className="shrink-0 rounded-md bg-accent px-2.5 text-xs font-semibold text-surface transition-colors hover:bg-accent-deep focus:outline-none focus-visible:ring-2 focus-visible:ring-accent/60 disabled:opacity-50"
            >
              {t('savedViews.save')}
            </button>
          </div>

          <div className="max-h-56 overflow-y-auto p-1">
            {views.length === 0 ? (
              <p className="px-2 py-3 text-center text-xs text-muted">{t('savedViews.empty')}</p>
            ) : (
              views.map((view) => (
                <div key={view.id} className="group flex items-center">
                  <button
                    type="button"
                    onClick={() => {
                      onApply(view.state);
                      setOpen(false);
                    }}
                    className="flex-1 truncate rounded-md px-2 py-1.5 text-left text-xs text-main transition-colors hover:bg-accent/5 hover:text-accent focus:outline-none focus-visible:bg-accent/10"
                    title={view.name}
                  >
                    {view.name}
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      deleteSavedView(storageKey, view.id);
                      refresh();
                    }}
                    aria-label={`${t('common.delete')}: ${view.name}`}
                    className="rounded-md p-1 text-muted/50 opacity-0 transition-opacity hover:bg-danger/10 hover:text-danger focus:opacity-100 focus:outline-none focus-visible:ring-2 focus-visible:ring-accent/60 group-hover:opacity-100"
                  >
                    <LuTrash2 className="h-3.5 w-3.5" aria-hidden />
                  </button>
                </div>
              ))
            )}
          </div>
        </div>
      )}
    </div>
  );
}
