import { LuEllipsisVertical, LuTrash2 } from 'react-icons/lu';
import { RowMenu } from '../../../components/ui/RowMenu';
import { EmptyState } from '../../../components/ui/EmptyState';
import { useT } from '../../../lib/i18n';
import { useClientPagination } from '../../../lib/useClientPagination';
import { PERMISSIONS } from '../../../lib/permissions';
import { useAuthStore } from '../../../store/authStore';
import { firstTextProperty, recordTitle } from '../cellValue';
import type { ValueResolver } from '../valueLabels';
import type { AppDetail, AppRecord } from '../types';

/** Cards per page — visual density choice for the grid layout. */
const GALLERY_PAGE_SIZE = 12;

/**
 * GALLERY view renderer — responsive card grid. No image pipeline exists for
 * records, so cards lead with the title (first TEXT property) plus up to three
 * labeled property rows.
 */
export function RecordGallery({
  app,
  records,
  isLoading,
  resolve,
  onRequestDelete,
}: {
  app: AppDetail;
  records: AppRecord[];
  isLoading: boolean;
  resolve: ValueResolver;
  onRequestDelete: (record: AppRecord) => void;
}) {
  const { t } = useT();
  const canDelete = useAuthStore((s) => s.hasAuthority(PERMISSIONS.APP_RECORD_DELETE));
  const { paged, page, setPage, pageSize, totalElements, totalPages } =
    useClientPagination(records, GALLERY_PAGE_SIZE);

  const titleProp = firstTextProperty(app.properties);
  const cardProps = app.properties.filter((p) => p.id !== titleProp?.id).slice(0, 3);
  const rangeStart = totalElements === 0 ? 0 : page * pageSize + 1;
  const rangeEnd = Math.min((page + 1) * pageSize, totalElements);

  return (
    <div className="flex flex-col gap-3">
      {isLoading ? (
        <div className="py-16 text-center text-xs text-muted">{t('common.loading')}</div>
      ) : records.length === 0 ? (
        <EmptyState message={t('apps.emptyRecords')} />
      ) : (
        <>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
            {paged.map((r) => (
              <article key={r.id} className="flex flex-col gap-2 rounded-xl border border-glass bg-bg/40 p-4">
                <div className="flex items-start justify-between gap-2">
                  <span className="min-w-0 break-words font-medium text-main">{recordTitle(r, titleProp, resolve)}</span>
                  {canDelete && (
                    <RowMenu
                      ariaLabel={t('common.actions')}
                      icon={LuEllipsisVertical}
                      items={[{ label: t('common.delete'), onClick: () => onRequestDelete(r), icon: LuTrash2, danger: true }]}
                    />
                  )}
                </div>
                {cardProps.some((p) => resolve(p, r) !== '') && (
                  <div className="mt-1 flex flex-col gap-1.5 border-t border-glass pt-2">
                    {cardProps.map((p) => {
                      const value = resolve(p, r);
                      return value ? (
                        <div key={p.id} className="flex items-baseline justify-between gap-2">
                          <span className="shrink-0 text-xs text-muted">{p.name}</span>
                          <span className="truncate text-sm text-main">{value}</span>
                        </div>
                      ) : null;
                    })}
                  </div>
                )}
              </article>
            ))}
          </div>
          <div className="flex items-center justify-end gap-3 text-xs text-muted">
            <span>
              {totalElements === 0 ? t('table.noItems') : t('table.showingRange', { from: rangeStart, to: rangeEnd, total: totalElements })}
            </span>
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={() => setPage(page - 1)}
                disabled={page === 0}
                className="rounded-md border border-glass bg-surface px-3 py-1 text-xs text-main transition-colors hover:bg-accent/5 disabled:opacity-40"
              >
                {t('table.prev')}
              </button>
              <span>{t('table.page', { current: page + 1, total: totalPages })}</span>
              <button
                type="button"
                onClick={() => setPage(page + 1)}
                disabled={page >= totalPages - 1}
                className="rounded-md border border-glass bg-surface px-3 py-1 text-xs text-main transition-colors hover:bg-accent/5 disabled:opacity-40"
              >
                {t('table.next')}
              </button>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
