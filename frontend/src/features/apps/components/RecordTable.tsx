import { useState } from 'react';
import { LuPencil, LuPlus, LuTrash2 } from 'react-icons/lu';
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
import { useClientPagination } from '../../../lib/useClientPagination';
import { PERMISSIONS } from '../../../lib/permissions';
import { useAuthStore } from '../../../store/authStore';
import { notify } from '../../../lib/notify';
import type { AppDetail, AppRecord } from '../types';
import { useRecords, usePatchRecord, useDeleteRecord } from '../hooks';
import { cellDisplay, cellEditValue, parseCellInput } from '../cellValue';
import { useValueResolvers } from '../valueLabels';
import { UserPicker } from '../../../components/pickers/UserPicker';
import { RelationPicker } from './RelationPicker';
import { RecordFormModal } from './RecordFormModal';

/** Cell being edited: which record × which property, plus the raw input draft. */
interface EditState {
  recordId: string;
  propertyId: string;
  draft: string;
}

/** Records fed by the parent (RecordsPanel) for client-mode TABLE views. */
export interface ClientRecordsOverride {
  records: AppRecord[];
  isLoading: boolean;
}

/**
 * TABLE view renderer — column = property, row = record. Two data modes:
 * self (default) — own server pagination + section chrome + create/delete dialogs;
 * override (client-mode) — RecordsPanel feeds filtered/sorted records, pagination
 * is local, create/delete live in the panel. All scalar types edit inline on
 * click; USER/RELATION cells edit through their pickers.
 */
export function RecordTable({
  app,
  override,
  onRequestDelete,
  onRequestEdit,
}: {
  app: AppDetail;
  override?: ClientRecordsOverride;
  /** Delete affordance for override mode (the panel owns the confirm dialog). */
  onRequestDelete?: (record: AppRecord) => void;
  /** Opens the panel-owned record form modal in edit mode (all modes delegate). */
  onRequestEdit?: (record: AppRecord) => void;
}) {
  const { t } = useT();
  const storageKey = `app-records-${app.id}`;
  const { page, setPage, pageSize, setPageSize, sort, toggleSort, listParams } =
    useListPageState({ defaultSort: { field: 'createdDate', dir: 'desc' }, storageKey });
  // Self-mode server query is disabled while the panel feeds records (override).
  const { data, isLoading, isFetching } = useRecords(override ? undefined : app.id, listParams);
  const client = useClientPagination(override?.records ?? [], 10, storageKey);
  const patch = usePatchRecord(app.id);
  const delRecord = useDeleteRecord(app.id);
  const canWrite = useAuthStore((s) => s.hasAuthority(PERMISSIONS.APP_RECORD_WRITE));
  const canDelete = useAuthStore((s) => s.hasAuthority(PERMISSIONS.APP_RECORD_DELETE));
  // USER labels resolve over the rows actually shown (override: panel-fed; self: server page).
  const resolve = useValueResolvers(app, override ? override.records : (data?.items ?? []));

  const [edit, setEdit] = useState<EditState | null>(null);
  const [creating, setCreating] = useState(false);
  const [deleting, setDeleting] = useState<AppRecord | null>(null);

  if (app.properties.length === 0) {
    // The panel normally guards this; keeps a standalone render meaningful.
    return <EmptyState message={t('apps.emptyProperties')} />;
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

  /** Picker commit path (USER/RELATION): the picker hands over a raw id or null. */
  const commitPicker = (record: AppRecord, propertyId: string, value: string | null) => {
    setEdit(null);
    const current = record.values[propertyId] ?? null;
    if ((value ?? null) === (current || null)) return;
    patch.mutate({ recordId: record.id, data: { values: { [propertyId]: value } } });
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
          if (prop.type === 'USER') {
            const raw = record.values[prop.id];
            return (
              <div className="min-w-36">
                <UserPicker
                  value={raw ? String(raw) : null}
                  valueLabel={raw ? resolve(prop, record) : undefined}
                  onChange={(v) => commitPicker(record, prop.id, v)}
                  size="sm"
                />
              </div>
            );
          }
          if (prop.type === 'RELATION') {
            const raw = record.values[prop.id];
            return (
              <div className="min-w-36">
                <RelationPicker
                  property={prop}
                  value={raw ? String(raw) : null}
                  valueLabel={raw ? resolve(prop, record) : undefined}
                  onChange={(v) => commitPicker(record, prop.id, v)}
                  size="sm"
                />
              </div>
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
        if (prop.type === 'USER' || prop.type === 'RELATION') {
          const display = resolve(prop, record);
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
        }
        const display = cellDisplay(prop, record);
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

  const table = (
    <DataTable<AppRecord>
      columns={columns}
      data={override ? client.paged : (data?.items ?? [])}
      rowKey={(r) => r.id}
      storageKey={storageKey}
      loading={override ? override.isLoading : isLoading || (isFetching && !data)}
      emptyMessage={t('apps.emptyRecords')}
      page={override ? client.page : (data?.page ?? page)}
      pageSize={override ? client.pageSize : (data?.size ?? pageSize)}
      pageSizeOptions={PAGE_SIZE_OPTIONS}
      onPageSizeChange={override ? client.setPageSize : setPageSize}
      totalElements={override ? client.totalElements : (data?.totalElements ?? 0)}
      totalPages={override ? client.totalPages : (data?.totalPages ?? 0)}
      onPageChange={override ? client.setPage : setPage}
      // Client-mode rows are already filtered/sorted by the panel — header sort
      // stays bound to the server whitelist only in self mode.
      {...(override ? {} : { sort, onSortChange: toggleSort })}
      actionsHeader={t('common.actions')}
      actions={(r) => (
        <RowMenu
          ariaLabel={t('common.actions')}
          items={[
            ...(canWrite
              ? [{ label: t('common.edit'), onClick: () => onRequestEdit?.(r), icon: LuPencil }]
              : []),
            ...(canDelete
              ? [
                  {
                    label: t('common.delete'),
                    onClick: () => (override ? onRequestDelete?.(r) : setDeleting(r)),
                    icon: LuTrash2,
                    danger: true,
                  },
                ]
              : []),
          ]}
        />
      )}
    />
  );

  // Override mode: the panel owns the section chrome (tabs, description, create,
  // delete dialog) — render the bare table. Self mode carries its own description
  // + create/delete, still inside the panel provided by the parent.
  if (override) return table;

  return (
    <>
      <div className="mb-4 flex items-center justify-between gap-3">
        <p className="m-0 text-sm text-muted">{t('apps.recordsDesc')}</p>
        {canWrite && (
          <Button variant="ghost" size="sm" onClick={() => setCreating(true)}>
            <LuPlus aria-hidden className="h-4 w-4" />
            {t('apps.newRecord')}
          </Button>
        )}
      </div>
      {table}

      {creating && <RecordFormModal app={app} onClose={() => setCreating(false)} />}

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
    </>
  );
}
