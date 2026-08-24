import { LuEllipsisVertical, LuLayoutGrid, LuPencil, LuTrash2 } from 'react-icons/lu';
import { RowMenu } from '../../../components/ui/RowMenu';
import { EmptyState } from '../../../components/ui/EmptyState';
import { TablePagination } from '../../../components/ui/TablePagination';
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
  onRequestEdit,
}: {
  app: AppDetail;
  records: AppRecord[];
  isLoading: boolean;
  resolve: ValueResolver;
  onRequestDelete: (record: AppRecord) => void;
  onRequestEdit: (record: AppRecord) => void;
}) {
  const { t } = useT();
  const canWrite = useAuthStore((s) => s.hasAuthority(PERMISSIONS.APP_RECORD_WRITE));
  const canDelete = useAuthStore((s) => s.hasAuthority(PERMISSIONS.APP_RECORD_DELETE));
  const { paged, page, setPage, pageSize, totalElements, totalPages } =
    useClientPagination(records, GALLERY_PAGE_SIZE);

  const titleProp = firstTextProperty(app.properties);
  const cardProps = app.properties.filter((p) => p.id !== titleProp?.id).slice(0, 3);

  return (
    <div className="flex flex-col gap-3">
      {isLoading ? (
        <div className="py-16 text-center text-xs text-muted">{t('common.loading')}</div>
      ) : records.length === 0 ? (
        <EmptyState message={t('apps.emptyRecords')} icon={LuLayoutGrid} />
      ) : (
        <>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
            {paged.map((r) => (
              <article key={r.id} className="flex flex-col gap-2 rounded-xl border border-glass bg-bg/40 p-4">
                <div className="flex items-start justify-between gap-2">
                  <span className="min-w-0 break-words font-medium text-main">{recordTitle(r, titleProp, resolve)}</span>
                  {(canWrite || canDelete) && (
                    <RowMenu
                      ariaLabel={t('common.actions')}
                      icon={LuEllipsisVertical}
                      items={[
                        ...(canWrite
                          ? [{ label: t('common.edit'), onClick: () => onRequestEdit(r), icon: LuPencil }]
                          : []),
                        ...(canDelete
                          ? [{ label: t('common.delete'), onClick: () => onRequestDelete(r), icon: LuTrash2, danger: true }]
                          : []),
                      ]}
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
          {/* Shared footer (DataTable-identical): fixed card page size — no rows-per-page. */}
          <TablePagination
            page={page}
            pageSize={pageSize}
            totalElements={totalElements}
            totalPages={totalPages}
            onPageChange={setPage}
          />
        </>
      )}
    </div>
  );
}
