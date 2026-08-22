import { LuPencil, LuTrash2 } from 'react-icons/lu';
import { Badge } from '../../../components/ui/Badge';
import { Button } from '../../../components/ui/Button';
import { EmptyState } from '../../../components/ui/EmptyState';
import { SelectInput } from '../../../components/ui/SelectInput';
import { useT } from '../../../lib/i18n';
import { PERMISSIONS } from '../../../lib/permissions';
import { useAuthStore } from '../../../store/authStore';
import { usePatchRecord } from '../hooks';
import { firstTextProperty, recordTitle } from '../cellValue';
import type { ValueResolver } from '../valueLabels';
import type { AppDetail, AppRecord, AppView } from '../types';

const COLUMN_TONES = ['muted', 'blue', 'green', 'accent', 'warning'] as const;

/**
 * BOARD view renderer (kanban) — columns come from the groupBy SELECT property's
 * configured options (plus a trailing "empty" bucket for records without a value).
 * No drag-drop: like the TaskBoard precedent, each card carries a compact select
 * that PATCHes the groupBy value to move it across columns.
 */
export function RecordBoard({
  app,
  view,
  records,
  isLoading,
  resolve,
  onRequestDelete,
  onRequestEdit,
}: {
  app: AppDetail;
  view: AppView;
  records: AppRecord[];
  isLoading: boolean;
  resolve: ValueResolver;
  onRequestDelete: (record: AppRecord) => void;
  onRequestEdit: (record: AppRecord) => void;
}) {
  const { t } = useT();
  const patch = usePatchRecord(app.id);
  const canWrite = useAuthStore((s) => s.hasAuthority(PERMISSIONS.APP_RECORD_WRITE));
  const canDelete = useAuthStore((s) => s.hasAuthority(PERMISSIONS.APP_RECORD_DELETE));

  const groupByProp = app.properties.find((p) => p.id === view.config?.groupBy);
  if (!groupByProp || groupByProp.type !== 'SELECT') {
    return <EmptyState message={t('apps.boardMissingGroupBy')} />;
  }

  const options = groupByProp.config?.options ?? [];
  const titleProp = firstTextProperty(app.properties);
  // Up to two non-groupBy summary lines per card.
  const summaryProps = app.properties.filter((p) => p.id !== groupByProp.id).slice(0, 2);

  const moveOptions = options.map((o) => ({ value: o, label: o }));
  const move = (record: AppRecord, value: string | null) =>
    patch.mutate({ recordId: record.id, data: { values: { [groupByProp.id]: value } } });

  const columnRecords = (option: string | null): AppRecord[] =>
    records.filter((r) => {
      const v = r.values[groupByProp.id];
      const cell = v === undefined || v === null ? null : String(v);
      return option === null ? !options.includes(cell ?? '') : cell === option;
    });

  return (
    <div className="flex gap-4 overflow-x-auto pb-2">
      {options.map((option, i) => (
        <BoardColumn
          key={option}
          label={option}
          tone={COLUMN_TONES[i % COLUMN_TONES.length]}
          records={columnRecords(option)}
          isLoading={isLoading}
          canWrite={canWrite}
          canDelete={canDelete}
          patchPending={patch.isPending}
          moveOptions={moveOptions}
          titleProp={titleProp}
          summaryProps={summaryProps}
          groupByPropId={groupByProp.id}
          resolve={resolve}
          onMove={move}
          onRequestDelete={onRequestDelete}
          onRequestEdit={onRequestEdit}
        />
      ))}
      <BoardColumn
        label={t('apps.boardEmptyColumn')}
        tone="muted"
        records={columnRecords(null)}
        isLoading={isLoading}
        canWrite={canWrite}
        canDelete={canDelete}
        patchPending={patch.isPending}
        moveOptions={moveOptions}
        titleProp={titleProp}
        summaryProps={summaryProps}
        groupByPropId={groupByProp.id}
        resolve={resolve}
        onMove={move}
        onRequestDelete={onRequestDelete}
        onRequestEdit={onRequestEdit}
      />
    </div>
  );
}

function BoardColumn({
  label,
  tone,
  records,
  isLoading,
  canWrite,
  canDelete,
  patchPending,
  moveOptions,
  titleProp,
  summaryProps,
  groupByPropId,
  resolve,
  onMove,
  onRequestDelete,
  onRequestEdit,
}: {
  label: string;
  tone: (typeof COLUMN_TONES)[number];
  records: AppRecord[];
  isLoading: boolean;
  canWrite: boolean;
  canDelete: boolean;
  patchPending: boolean;
  moveOptions: { value: string; label: string }[];
  titleProp: ReturnType<typeof firstTextProperty>;
  summaryProps: AppDetail['properties'];
  groupByPropId: string;
  resolve: ValueResolver;
  onMove: (record: AppRecord, value: string | null) => void;
  onRequestDelete: (record: AppRecord) => void;
  onRequestEdit: (record: AppRecord) => void;
}) {
  const { t } = useT();
  return (
    <section className="flex min-h-[12rem] w-72 shrink-0 flex-col gap-3 rounded-xl border border-glass bg-surface/40 p-3">
      <div className="flex items-center justify-between px-1">
        <Badge tone={tone}>{label}</Badge>
        <span className="text-xs text-muted">{records.length}</span>
      </div>
      {isLoading ? (
        <div className="py-6 text-center text-xs text-muted">{t('common.loading')}</div>
      ) : records.length === 0 ? (
        <div className="rounded-lg border border-dashed border-glass px-3 py-6 text-center text-xs text-muted">
          {t('apps.emptyRecords')}
        </div>
      ) : (
        <div className="flex flex-col gap-2">
          {records.map((record) => {
            const current = record.values[groupByPropId];
            return (
              <article key={record.id} className="flex flex-col gap-2 rounded-lg border border-glass bg-bg/40 p-3">
                <span className="font-medium text-main">{recordTitle(record, titleProp, resolve)}</span>
                {summaryProps.some((p) => resolve(p, record) !== '') && (
                  <div className="flex flex-col gap-0.5">
                    {summaryProps.map((p) => {
                      const value = resolve(p, record);
                      return value ? (
                        <span key={p.id} className="truncate text-xs text-muted">
                          {p.name}: {value}
                        </span>
                      ) : null;
                    })}
                  </div>
                )}
                <div className="flex items-center gap-2 border-t border-glass pt-2">
                  {canWrite && (
                    <div className="w-36">
                      <SelectInput
                        size="sm"
                        options={moveOptions}
                        value={
                          current != null && moveOptions.some((o) => o.value === current)
                            ? { value: String(current), label: String(current) }
                            : null
                        }
                        onChange={(o) => onMove(record, (o as { value: string } | null)?.value ?? null)}
                        isClearable
                        placeholder={t('apps.boardMoveTo')}
                        isDisabled={patchPending}
                      />
                    </div>
                  )}
                  {(canWrite || canDelete) && (
                    <div className="ml-auto flex items-center gap-1">
                      {canWrite && (
                        <Button
                          variant="ghost"
                          size="sm"
                          aria-label={t('common.edit')}
                          title={t('common.edit')}
                          onClick={() => onRequestEdit(record)}
                        >
                          <LuPencil aria-hidden className="h-4 w-4" />
                        </Button>
                      )}
                      {canDelete && (
                        <Button
                          variant="ghost"
                          size="sm"
                          className="text-danger hover:bg-danger/10 hover:text-danger"
                          aria-label={t('common.delete')}
                          title={t('common.delete')}
                          onClick={() => onRequestDelete(record)}
                        >
                          <LuTrash2 aria-hidden className="h-4 w-4" />
                        </Button>
                      )}
                    </div>
                  )}
                </div>
              </article>
            );
          })}
        </div>
      )}
    </section>
  );
}
