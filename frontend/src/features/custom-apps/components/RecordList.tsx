import { LuPencil, LuTrash2 } from 'react-icons/lu';
import { DataTable, type Column } from '../../../components/ui/DataTable';
import { RowMenu } from '../../../components/ui/RowMenu';
import { useT } from '../../../lib/i18n';
import { formatDateTime } from '../../../lib/format';
import { PAGE_SIZE_OPTIONS } from '../../../lib/pagination';
import { useClientPagination } from '../../../lib/useClientPagination';
import { PERMISSIONS } from '../../../lib/permissions';
import { useAuthStore } from '../../../store/authStore';
import { firstTextProperty, recordTitle } from '../cellValue';
import type { ValueResolver } from '../valueLabels';
import type { CustomAppDetail, CustomAppRecord } from '../types';

/**
 * LIST view renderer — compact rows: the record title (first TEXT property) with
 * up to three further cells as a muted inline summary. Client-side pagination over
 * the panel-provided records.
 */
export function RecordList({
  customApp,
  records,
  isLoading,
  resolve,
  onRequestDelete,
  onRequestEdit,
}: {
  customApp: CustomAppDetail;
  records: CustomAppRecord[];
  isLoading: boolean;
  resolve: ValueResolver;
  onRequestDelete: (record: CustomAppRecord) => void;
  onRequestEdit: (record: CustomAppRecord) => void;
}) {
  const { t } = useT();
  const canWrite = useAuthStore((s) => s.hasAuthority(PERMISSIONS.CUSTOM_APP_RECORD_WRITE));
  const canDelete = useAuthStore((s) => s.hasAuthority(PERMISSIONS.CUSTOM_APP_RECORD_DELETE));
  const { paged, page, setPage, pageSize, setPageSize, totalElements, totalPages } = useClientPagination(records);

  const titleProp = firstTextProperty(customApp.properties);
  const summaryProps = customApp.properties.filter((p) => p.id !== titleProp?.id).slice(0, 3);

  const columns: Column<CustomAppRecord>[] = [
    {
      key: 'title',
      header: titleProp?.name ?? t('customApps.recordTitleCol'),
      render: (r) => (
        <div className="flex min-w-0 flex-col">
          <span className="truncate font-medium text-main">{recordTitle(r, titleProp, resolve)}</span>
          {summaryProps.some((p) => resolve(p, r) !== '') && (
            <span className="truncate text-xs text-muted">
              {summaryProps
                .map((p) => ({ p, v: resolve(p, r) }))
                .filter(({ v }) => v !== '')
                .map(({ p, v }) => `${p.name}: ${v}`)
                .join(' · ')}
            </span>
          )}
        </div>
      ),
    },
    {
      key: 'createdDate',
      header: t('customApps.createdDate'),
      render: (r) => <span className="whitespace-nowrap text-muted">{formatDateTime(r.createdDate)}</span>,
    },
  ];

  return (
    <DataTable<CustomAppRecord>
      columns={columns}
      data={paged}
      rowKey={(r) => r.id}
      loading={isLoading}
      emptyMessage={t('customApps.emptyRecords')}
      page={page}
      pageSize={pageSize}
      pageSizeOptions={PAGE_SIZE_OPTIONS}
      onPageSizeChange={setPageSize}
      totalElements={totalElements}
      totalPages={totalPages}
      onPageChange={setPage}
      actionsHeader={t('common.actions')}
      actions={(r) => (
        <RowMenu
          ariaLabel={t('common.actions')}
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
    />
  );
}
