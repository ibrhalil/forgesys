import { useState } from 'react';
import { LuPlus, LuTrash2 } from 'react-icons/lu';
import { DetailPanel } from '../../../components/detail/DetailPanel';
import { DataTable, type Column } from '../../../components/ui/DataTable';
import { RowMenu } from '../../../components/ui/RowMenu';
import { Button } from '../../../components/ui/Button';
import { ConfirmDialog } from '../../../components/ui/ConfirmDialog';
import { EmptyState } from '../../../components/ui/EmptyState';
import { SelectInput } from '../../../components/ui/SelectInput';
import { PAGE_SIZE_OPTIONS } from '../../../lib/pagination';
import { formatDateTime } from '../../../lib/format';
import { useT } from '../../../lib/i18n';
import { useListPageState } from '../../../lib/useListPageState';
import { PERMISSIONS } from '../../../lib/permissions';
import { useAuthStore } from '../../../store/authStore';
import { notify } from '../../../lib/notify';
import type { AppDetail, AppRecord } from '../types';
import { useRecords, usePatchRecord, useDeleteRecord } from '../hooks';
import { cellDisplay, cellEditValue, parseCellInput } from '../cellValue';
import { NewRecordModal } from './NewRecordModal';

/** Cell being edited: which record × which property, plus the raw input draft. */
interface EditState {
  recordId: string;
  propertyId: string;
  draft: string;
}

/**
 * TABLE view renderer — column = property, row = record. Simple scalar cells
 * (TEXT/NUMBER/SELECT/DATE) edit inline on click; USER/RELATION show a shortened
 * raw id until their pickers arrive in a later part.
 */
export function RecordTable({ app }: { app: AppDetail }) {
  const { t } = useT();
  const { page, setPage, pageSize, setPageSize, sort, toggleSort } =
    useListPageState({ defaultSort: { field: 'createdDate', dir: 'desc' } });
  const { data, isLoading, isFetching } = useRecords(app.id, { page, size: pageSize, sorts: [sort] });
  const patch = usePatchRecord(app.id);
  const delRecord = useDeleteRecord(app.id);
  const canWrite = useAuthStore((s) => s.hasAuthority(PERMISSIONS.APP_RECORD_WRITE));
  const canDelete = useAuthStore((s) => s.hasAuthority(PERMISSIONS.APP_RECORD_DELETE));

  const [edit, setEdit] = useState<EditState | null>(null);
  const [creating, setCreating] = useState(false);
  const [deleting, setDeleting] = useState<AppRecord | null>(null);

  if (app.properties.length === 0) {
    return (
      <DetailPanel title={t('apps.recordsSection')}>
        <EmptyState message={t('apps.emptyProperties')} />
      </DetailPanel>
    );
  }

  const commit = (record: AppRecord, propertyId: string, raw: string) => {
    const prop = app.properties.find((p) => p.id === propertyId)!;
    const parsed = parseCellInput(prop, raw);
    if (parsed === undefined) {
      notify.error(t('apps.invalidCellInput'));
      return;
    }
    setEdit(null);
    const current = record.values[propertyId] ?? null;
    if (parsed === current) return;
    // Silent failure keeps the table on the old value; the global toast explains.
    patch.mutate({ recordId: record.id, data: { values: { [propertyId]: parsed } } });
  };

  const columns: Column<AppRecord>[] = [
    ...app.properties.map((prop): Column<AppRecord> => ({
      key: prop.id,
      header: prop.name,
      render: (record) => {
        const editing = edit?.recordId === record.id && edit.propertyId === prop.id;
        if (editing) {
          if (prop.type === 'SELECT') {
            const options = (prop.config?.options ?? []).map((o) => ({ value: o, label: o }));
            return (
              <SelectInput
                options={options}
                value={options.find((o) => o.value === edit.draft) ?? null}
                onChange={(o) => {
                  const value = (o as { value: string } | null)?.value;
                  if (value) commit(record, prop.id, value);
                }}
                isClearable
                size="sm"
                className="min-w-32"
              />
            );
          }
          return (
            <input
              autoFocus
              type={prop.type === 'NUMBER' ? 'number' : prop.type === 'DATE' ? 'date' : 'text'}
              value={edit.draft}
              onChange={(e) => setEdit({ ...edit, draft: e.target.value })}
              onKeyDown={(e) => {
                if (e.key === 'Enter') commit(record, prop.id, edit.draft);
                if (e.key === 'Escape') setEdit(null);
              }}
              onBlur={() => commit(record, prop.id, edit.draft)}
              className="w-full min-w-24 rounded-md border border-accent/60 bg-main/5 px-2 py-1 text-sm text-main focus:outline-none focus:ring-2 focus:ring-accent/50"
            />
          );
        }
        const display = cellDisplay(prop, record);
        if (prop.type === 'USER' || prop.type === 'RELATION') {
          // Raw id until pickers arrive — shortened, full id on hover.
          return display ? (
            <span className="font-mono text-xs text-muted" title={String(record.values[prop.id] ?? '')}>
              {display}
            </span>
          ) : (
            <span className="text-muted/50">—</span>
          );
        }
        if (!canWrite) {
          return display ? <span>{display}</span> : <span className="text-muted/50">—</span>;
        }
        return (
          <button
            type="button"
            title={t('common.edit')}
            onClick={() => setEdit({ recordId: record.id, propertyId: prop.id, draft: cellEditValue(prop, record) })}
            className="max-w-48 truncate rounded px-1 text-left text-sm text-main transition-colors hover:bg-accent/10 hover:text-accent"
          >
            {display || <span className="text-muted/50">—</span>}
          </button>
        );
      },
    })),
    {
      key: 'createdDate',
      header: t('apps.createdDate'),
      sortKey: 'createdDate',
      render: (r) => <span className="text-muted">{formatDateTime(r.createdDate)}</span>,
    },
  ];

  return (
    <DetailPanel title={t('apps.recordsSection')}>
      <div className="mb-4 flex items-center justify-between gap-3">
        <p className="m-0 text-sm text-muted">{t('apps.recordsDesc')}</p>
        {canWrite && (
          <Button variant="ghost" size="sm" onClick={() => setCreating(true)}>
            <LuPlus aria-hidden className="h-4 w-4" />
            {t('apps.newRecord')}
          </Button>
        )}
      </div>
      <DataTable<AppRecord>
        columns={columns}
        data={data?.items ?? []}
        rowKey={(r) => r.id}
        loading={isLoading || (isFetching && !data)}
        emptyMessage={t('apps.emptyRecords')}
        page={data?.page ?? page}
        pageSize={data?.size ?? pageSize}
        pageSizeOptions={PAGE_SIZE_OPTIONS}
        onPageSizeChange={setPageSize}
        totalElements={data?.totalElements ?? 0}
        totalPages={data?.totalPages ?? 0}
        onPageChange={setPage}
        sort={sort}
        onSortChange={toggleSort}
        actionsHeader={t('common.actions')}
        actions={(r) => (
          <RowMenu
            ariaLabel={t('common.actions')}
            items={
              canDelete
                ? [{ label: t('common.delete'), onClick: () => setDeleting(r), icon: LuTrash2, danger: true }]
                : []
            }
          />
        )}
      />

      {creating && <NewRecordModal app={app} onClose={() => setCreating(false)} />}

      <ConfirmDialog
        open={!!deleting}
        title={t('apps.deleteRecordTitle')}
        message={t('apps.deleteRecordMsg')}
        confirmText={t('common.delete')}
        danger
        loading={delRecord.isPending}
        onConfirm={async () => {
          if (!deleting) return;
          try {
            await delRecord.mutateAsync(deleting.id);
            notify.success(t('apps.recordDeleted'));
            setDeleting(null);
          } catch {
            /* global toast */
          }
        }}
        onClose={() => setDeleting(null)}
      />
    </DetailPanel>
  );
}
