import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import {
  closestCorners,
  DndContext,
  DragOverlay,
  PointerSensor,
  TouchSensor,
  useDraggable,
  useDroppable,
  useSensor,
  useSensors,
} from '@dnd-kit/core';
import type { DragEndEvent } from '@dnd-kit/core';
import { LuEllipsisVertical, LuPencil, LuTrash2 } from 'react-icons/lu';
import { Badge } from '../../../components/ui/Badge';
import { EmptyState } from '../../../components/ui/EmptyState';
import { RowMenu } from '../../../components/ui/RowMenu';
import { SelectInput } from '../../../components/ui/SelectInput';
import { useT } from '../../../lib/i18n';
import { cn } from '../../../lib/cn';
import { PERMISSIONS } from '../../../lib/permissions';
import { useAuthStore } from '../../../store/authStore';
import { columnDropId, EMPTY_DROP_ID, resolveDrop, type DropColumn } from '../../../lib/boardDnd';
import { usePatchRecord, VIEW_RECORDS_PARAMS } from '../hooks';
import { firstTextProperty, recordTitle } from '../cellValue';
import type { PageResult } from '../../../types';
import type { ValueResolver } from '../valueLabels';
import type { CustomAppDetail, CustomAppRecord, CustomAppView } from '../types';

const COLUMN_TONES = ['muted', 'blue', 'green', 'accent', 'warning'] as const;

/**
 * BOARD view renderer (kanban) — columns come from the groupBy SELECT property's
 * configured options (plus a trailing "empty" bucket for records without a
 * value). Cards are dragged between droppable columns (@dnd-kit) with an
 * optimistic cache write + rollback on error; the per-card compact select stays
 * as the keyboard/touch alternative.
 */
export function RecordBoard({
  customApp,
  view,
  records,
  isLoading,
  resolve,
  onRequestDelete,
  onRequestEdit,
}: {
  customApp: CustomAppDetail;
  view: CustomAppView;
  records: CustomAppRecord[];
  isLoading: boolean;
  resolve: ValueResolver;
  onRequestDelete: (record: CustomAppRecord) => void;
  onRequestEdit: (record: CustomAppRecord) => void;
}) {
  const { t } = useT();
  const qc = useQueryClient();
  const patch = usePatchRecord(customApp.id);
  const canWrite = useAuthStore((s) => s.hasAuthority(PERMISSIONS.CUSTOM_APP_RECORD_WRITE));
  const canDelete = useAuthStore((s) => s.hasAuthority(PERMISSIONS.CUSTOM_APP_RECORD_DELETE));
  const [dragging, setDragging] = useState<CustomAppRecord | null>(null);

  // PointerSensor distance keeps plain clicks (RowMenu, mover) from starting a
  // drag; TouchSensor delay keeps touch scrolling usable.
  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 5 } }),
    useSensor(TouchSensor, { activationConstraint: { delay: 200, tolerance: 8 } }),
  );

  const groupByProp = customApp.properties.find((p) => p.id === view.config?.groupBy);
  if (!groupByProp || groupByProp.type !== 'SELECT') {
    return <EmptyState message={t('customApps.boardMissingGroupBy')} />;
  }

  const options = groupByProp.config?.options ?? [];
  const titleProp = firstTextProperty(customApp.properties);
  // Up to two non-groupBy summary lines per card.
  const summaryProps = customApp.properties.filter((p) => p.id !== groupByProp.id).slice(0, 2);

  const moveOptions = options.map((o) => ({ value: o, label: o }));
  const dropColumns: DropColumn<string | null>[] = [
    ...options.map((o) => ({ id: columnDropId(o), value: o })),
    { id: EMPTY_DROP_ID, value: null },
  ];

  /**
   * Move a record to another column — one code path for the drag-drop overlay
   * and the card's mover select. The optimistic write targets the underlying
   * records query cache (not the filtered `records` prop), so RecordsPanel's
   * grouping recomputes; rollback restores the snapshot on error while the
   * global mutations.onError raises the toast.
   */
  const move = (record: CustomAppRecord, value: string | null) => {
    const key = ['customApps', customApp.id, 'records', VIEW_RECORDS_PARAMS];
    const prev = qc.getQueryData<PageResult<CustomAppRecord>>(key);
    qc.setQueryData<PageResult<CustomAppRecord>>(key, (cur) =>
      cur
        ? {
            ...cur,
            items: cur.items.map((r) =>
              r.id === record.id ? { ...r, values: { ...r.values, [groupByProp.id]: value } } : r,
            ),
          }
        : cur,
    );
    patch.mutate(
      { recordId: record.id, data: { values: { [groupByProp.id]: value } } },
      { onError: () => { if (prev) qc.setQueryData(key, prev); } },
    );
  };

  const cellValue = (record: CustomAppRecord): string | null => {
    const v = record.values[groupByProp.id];
    return v === undefined || v === null ? null : String(v);
  };

  const endDrag = () => {
    setDragging(null);
    document.body.classList.remove('cursor-grabbing');
  };

  const onDragEnd = (event: DragEndEvent) => {
    endDrag();
    const record = records.find((r) => r.id === String(event.active.id));
    if (!record) return;
    const next = resolveDrop(event.over?.id, cellValue(record), dropColumns);
    if (next !== undefined) move(record, next);
  };

  return (
    <DndContext
      sensors={sensors}
      collisionDetection={closestCorners}
      onDragStart={(event) => {
        setDragging(records.find((r) => r.id === String(event.active.id)) ?? null);
        document.body.classList.add('cursor-grabbing');
      }}
      onDragEnd={onDragEnd}
      onDragCancel={endDrag}
    >
      <div className="flex gap-4 overflow-x-auto pb-2">
        {options.map((option, i) => (
          <BoardColumn
            key={option}
            dropId={columnDropId(option)}
            label={option}
            tone={COLUMN_TONES[i % COLUMN_TONES.length]}
            records={records.filter((r) => cellValue(r) === option)}
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
          dropId={EMPTY_DROP_ID}
          label={t('customApps.boardEmptyColumn')}
          tone="muted"
          records={records.filter((r) => {
            const cell = cellValue(r);
            return cell === null || !options.includes(cell);
          })}
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

      <DragOverlay>
        {dragging && (
          <RecordDragPreview record={dragging} titleProp={titleProp} summaryProps={summaryProps} resolve={resolve} />
        )}
      </DragOverlay>
    </DndContext>
  );
}

function BoardColumn({
  dropId,
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
  dropId: string;
  label: string;
  tone: (typeof COLUMN_TONES)[number];
  records: CustomAppRecord[];
  isLoading: boolean;
  canWrite: boolean;
  canDelete: boolean;
  patchPending: boolean;
  moveOptions: { value: string; label: string }[];
  titleProp: ReturnType<typeof firstTextProperty>;
  summaryProps: CustomAppDetail['properties'];
  groupByPropId: string;
  resolve: ValueResolver;
  onMove: (record: CustomAppRecord, value: string | null) => void;
  onRequestDelete: (record: CustomAppRecord) => void;
  onRequestEdit: (record: CustomAppRecord) => void;
}) {
  const { t } = useT();
  const { setNodeRef, isOver } = useDroppable({ id: dropId });

  return (
    <section
      ref={setNodeRef}
      data-droppable-id={dropId}
      className={cn(
        'flex min-h-[12rem] w-72 shrink-0 flex-col gap-3 rounded-xl border border-glass bg-surface/40 p-3',
        isOver && 'ring-2 ring-accent/40 bg-accent/5',
      )}
    >
      <div className="flex items-center justify-between px-1">
        <Badge tone={tone}>{label}</Badge>
        <span className="text-xs text-muted">{records.length}</span>
      </div>
      {isLoading ? (
        <div className="py-6 text-center text-xs text-muted">{t('common.loading')}</div>
      ) : records.length === 0 ? (
        <div className="rounded-lg border border-dashed border-glass px-3 py-6 text-center text-xs text-muted">
          {t('customApps.emptyRecords')}
        </div>
      ) : (
        <div className="flex flex-col gap-2">
          {records.map((record) => (
            <RecordCard
              key={record.id}
              record={record}
              canWrite={canWrite}
              canDelete={canDelete}
              patchPending={patchPending}
              moveOptions={moveOptions}
              titleProp={titleProp}
              summaryProps={summaryProps}
              groupByPropId={groupByPropId}
              resolve={resolve}
              onMove={onMove}
              onRequestDelete={onRequestDelete}
              onRequestEdit={onRequestEdit}
            />
          ))}
        </div>
      )}
    </section>
  );
}

function RecordCard({
  record,
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
  record: CustomAppRecord;
  canWrite: boolean;
  canDelete: boolean;
  patchPending: boolean;
  moveOptions: { value: string; label: string }[];
  titleProp: ReturnType<typeof firstTextProperty>;
  summaryProps: CustomAppDetail['properties'];
  groupByPropId: string;
  resolve: ValueResolver;
  onMove: (record: CustomAppRecord, value: string | null) => void;
  onRequestDelete: (record: CustomAppRecord) => void;
  onRequestEdit: (record: CustomAppRecord) => void;
}) {
  const { t } = useT();
  const { attributes, listeners, setNodeRef, isDragging } = useDraggable({
    id: record.id,
    disabled: !canWrite,
  });
  const current = record.values[groupByPropId];

  return (
    <article
      ref={setNodeRef}
      {...(canWrite ? { ...attributes, ...listeners } : {})}
      style={canWrite ? { touchAction: 'manipulation' } : undefined}
      className={cn(
        'flex flex-col gap-2 rounded-lg border border-glass bg-bg/40 p-3',
        canWrite && !isDragging && 'cursor-grab',
        isDragging && 'opacity-40',
      )}
    >
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
        {/* Keyboard/touch alternative to dragging — same optimistic move path. */}
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
              placeholder={t('customApps.boardMoveTo')}
              isDisabled={patchPending}
            />
          </div>
        )}
        {(canWrite || canDelete) && (
          <div className="ml-auto">
            <RowMenu
              ariaLabel={t('common.actions')}
              icon={LuEllipsisVertical}
              items={[
                ...(canWrite
                  ? [{ label: t('common.edit'), onClick: () => onRequestEdit(record), icon: LuPencil }]
                  : []),
                ...(canDelete
                  ? [{ label: t('common.delete'), onClick: () => onRequestDelete(record), icon: LuTrash2, danger: true }]
                  : []),
              ]}
            />
          </div>
        )}
      </div>
    </article>
  );
}

/** Simplified clone shown in the DragOverlay — title + summary lines, no action footer. */
function RecordDragPreview({
  record,
  titleProp,
  summaryProps,
  resolve,
}: {
  record: CustomAppRecord;
  titleProp: ReturnType<typeof firstTextProperty>;
  summaryProps: CustomAppDetail['properties'];
  resolve: ValueResolver;
}) {
  return (
    <article className="flex w-64 rotate-2 flex-col gap-2 rounded-lg border border-glass bg-surface p-3 shadow-2xl">
      <span className="font-medium text-main">{recordTitle(record, titleProp, resolve)}</span>
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
    </article>
  );
}
