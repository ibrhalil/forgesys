import { PERMISSIONS } from '../../../lib/permissions';
import { useMemo, useState } from 'react';
import type { Task, TaskPriority, TaskStatus, TaskRequest } from '../types';
import { useUsers } from '../../users/hooks';
import { useTasks, useCreateTask, useUpdateTask, useDeleteTask } from '../hooks';
import { notify, extractFieldErrors } from '../../../lib/notify';
import { Modal } from '../../../components/ui/Modal';
import { ConfirmDialog } from '../../../components/ui/ConfirmDialog';
import { Button } from '../../../components/ui/Button';
import { Badge } from '../../../components/ui/Badge';
import { TextField } from '../../../components/ui/Field';
import { TextAreaField } from '../../../components/ui/TextArea';
import { SelectInput } from '../../../components/ui/SelectInput';
import type { SelectOption } from '../../../lib/select';
import { useT } from '../../../lib/i18n';
import { formatDate } from '../../../lib/format';
import { useAuthStore } from '../../../store/authStore';

const COLUMN_TONES: Record<TaskStatus, 'muted' | 'blue' | 'green'> = {
  TODO: 'muted',
  IN_PROGRESS: 'blue',
  DONE: 'green',
};

/** Column + select option labels resolved per-locale at render time. */
function useTaskLabels() {
  const { t } = useT();
  const statusLabel: Record<TaskStatus, string> = {
    TODO: t('tasks.col.todo'),
    IN_PROGRESS: t('tasks.col.inProgress'),
    DONE: t('tasks.col.done'),
  };
  const statusOptions = (Object.keys(statusLabel) as TaskStatus[]).map(
    (status) => ({ value: status, label: statusLabel[status] }),
  );
  const priorityOptions: SelectOption<TaskPriority>[] = [
    { value: 'LOW', label: t('tasks.priorityLow') },
    { value: 'MEDIUM', label: t('tasks.priorityMedium') },
    { value: 'HIGH', label: t('tasks.priorityHigh') },
  ];
  const priorityLabel: Record<TaskPriority, string> = {
    LOW: t('tasks.priorityLow'),
    MEDIUM: t('tasks.priorityMedium'),
    HIGH: t('tasks.priorityHigh'),
  };
  return { statusLabel, statusOptions, priorityOptions, priorityLabel };
}

const PRIORITY_TONE: Record<TaskPriority, 'danger' | 'warning' | 'muted'> = {
  HIGH: 'danger',
  MEDIUM: 'warning',
  LOW: 'muted',
};

function toRequest(task: Task, overrides: Partial<TaskRequest> = {}): TaskRequest {
  return {
    title: task.title,
    description: task.description ?? undefined,
    status: task.status,
    priority: task.priority,
    assigneeId: task.assigneeId ?? undefined,
    dueDate: task.dueDate ?? undefined,
    ...overrides,
  };
}

/**
 * Three-column task board (TODO/IN_PROGRESS/DONE). No drag-drop — a card carries its own
 * status select so a user moves it across columns by changing the value (optimistic via
 * invalidate). Tasks are fetched as one list and grouped client-side.
 */
export function TaskBoard({ projectId }: { projectId: string }) {
  const { t } = useT();
  const { statusLabel } = useTaskLabels();
  const canWrite = useAuthStore((s) => s.hasAuthority(PERMISSIONS.TASK_WRITE));
  const canDelete = useAuthStore((s) => s.hasAuthority(PERMISSIONS.TASK_DELETE));
  const { data: tasks, isLoading } = useTasks(projectId);
  const { data: usersPage } = useUsers({ size: 100 });
  const users = useMemo(() => usersPage?.items ?? [], [usersPage]);
  const usersById = useMemo(() => {
    const m = new Map<string, string>();
    users.forEach((u) => m.set(u.id, u.email));
    return m;
  }, [users]);

  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState<Task | null>(null);
  const [deleting, setDeleting] = useState<Task | null>(null);
  const delTask = useDeleteTask();

  return (
    <div className="flex flex-col gap-5">
      <header className="flex items-center justify-between gap-4">
        <h2 className="m-0 text-xl font-semibold text-main">{t('tasks.title')}</h2>
        {canWrite && <Button variant="primary" onClick={() => setCreating(true)}>{t('tasks.new')}</Button>}
      </header>

      <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
        {(Object.keys(COLUMN_TONES) as TaskStatus[]).map((status) => {
          const colTasks = (tasks ?? []).filter((tk) => tk.status === status);
          return (
            <section key={status} className="flex min-h-[12rem] flex-col gap-3 rounded-xl border border-glass bg-surface/40 p-3">
              <div className="flex items-center justify-between px-1">
                <div className="flex items-center gap-2">
                  <Badge tone={COLUMN_TONES[status]}>{statusLabel[status]}</Badge>
                </div>
                <span className="text-xs text-muted">{colTasks.length}</span>
              </div>

              {isLoading ? (
                <div className="py-6 text-center text-xs text-muted">{t('common.loading')}</div>
              ) : colTasks.length === 0 ? (
                <div className="rounded-lg border border-dashed border-glass px-3 py-6 text-center text-xs text-muted">{t('tasks.noTasks')}</div>
              ) : (
                <div className="flex flex-col gap-2">
                  {colTasks.map((task) => (
                    <TaskCard
                      key={task.id}
                      task={task}
                      projectId={projectId}
                      usersById={usersById}
                      canWrite={canWrite}
                      canDelete={canDelete}
                      onEdit={() => setEditing(task)}
                      onDelete={() => setDeleting(task)}
                    />
                  ))}
                </div>
              )}
            </section>
          );
        })}
      </div>

      {creating && (
        <TaskModal projectId={projectId} users={users} onClose={() => setCreating(false)} />
      )}
      {editing && (
        <TaskModal projectId={projectId} task={editing} users={users} onClose={() => setEditing(null)} />
      )}

      <ConfirmDialog
        open={!!deleting}
        title={t('tasks.deleteTitle')}
        message={t('tasks.deleteMsg', { title: deleting?.title ?? '' })}
        confirmText={t('common.delete')}
        danger
        loading={delTask.isPending}
        onConfirm={async () => {
          if (!deleting) return;
          try {
            await delTask.mutateAsync({ projectId, taskId: deleting.id });
            notify.success(t('tasks.deleted'));
            setDeleting(null);
          } catch {
            /* global toast */
          }
        }}
        onClose={() => setDeleting(null)}
      />
    </div>
  );
}

function TaskCard({
  task,
  projectId,
  usersById,
  canWrite,
  canDelete,
  onEdit,
  onDelete,
}: {
  task: Task;
  projectId: string;
  usersById: Map<string, string>;
  canWrite: boolean;
  canDelete: boolean;
  onEdit: () => void;
  onDelete: () => void;
}) {
  const { t } = useT();
  const { statusOptions, priorityLabel } = useTaskLabels();
  const updateTask = useUpdateTask();
  const move = (status: TaskStatus) =>
    updateTask.mutate({ projectId, taskId: task.id, data: toRequest(task, { status }) });

  return (
    <article className="flex flex-col gap-2 rounded-lg border border-glass bg-bg/40 p-3">
      <div className="flex items-start justify-between gap-2">
        <span className="font-medium text-main">{task.title}</span>
        <Badge tone={PRIORITY_TONE[task.priority]}>{priorityLabel[task.priority]}</Badge>
      </div>
      {task.description && <p className="line-clamp-2 text-xs text-muted">{task.description}</p>}
      <div className="flex items-center justify-between text-xs text-muted">
        <span className="truncate">
          {task.assigneeId ? usersById.get(task.assigneeId) ?? t('tasks.unassigned') : t('tasks.unassigned')}
        </span>
        {task.dueDate && <span className="whitespace-nowrap">{formatDate(task.dueDate)}</span>}
      </div>
      <div className="flex items-center gap-2 border-t border-glass pt-2">
        {/* Moving a task across columns is a write — hide the mover without pm:task:write. */}
        {canWrite && (
          <div className="w-36">
            <SelectInput
              size="sm"
              options={statusOptions}
              value={statusOptions.find((o) => o.value === task.status) ?? null}
              onChange={(next) => move((next as SelectOption<TaskStatus> | null)?.value ?? task.status)}
              isDisabled={updateTask.isPending}
            />
          </div>
        )}
        {/* Same action pattern as table rows/detail headers: sm buttons, gated by authority. */}
        {canWrite && <Button size="sm" variant="ghost" className="ml-auto" onClick={onEdit}>{t('common.edit')}</Button>}
        {canDelete && <Button size="sm" variant="danger" onClick={onDelete}>{t('common.delete')}</Button>}
      </div>
    </article>
  );
}

function TaskModal({
  projectId,
  task,
  users,
  onClose,
}: {
  projectId: string;
  task?: Task;
  users: { id: string; email: string }[];
  onClose: () => void;
}) {
  const { t } = useT();
  const { statusOptions, priorityOptions } = useTaskLabels();
  const create = useCreateTask();
  const update = useUpdateTask();
  const isEdit = !!task;

  const [title, setTitle] = useState(task?.title ?? '');
  const [description, setDescription] = useState(task?.description ?? '');
  const [status, setStatus] = useState<TaskStatus>(task?.status ?? 'TODO');
  const [priority, setPriority] = useState<TaskPriority>(task?.priority ?? 'MEDIUM');
  const [assigneeId, setAssigneeId] = useState<string>(task?.assigneeId ?? '');
  const [dueDate, setDueDate] = useState<string>(task?.dueDate ?? '');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const submit = async () => {
    setFieldErrors({});
    const data: TaskRequest = {
      title,
      description: description || undefined,
      status,
      priority,
      assigneeId: assigneeId || undefined,
      dueDate: dueDate || undefined,
    };
    try {
      if (isEdit && task) {
        await update.mutateAsync({ projectId, taskId: task.id, data });
        notify.success(t('tasks.updated'));
      } else {
        await create.mutateAsync({ projectId, data });
        notify.success(t('tasks.created'));
      }
      onClose();
    } catch (e) {
      setFieldErrors(extractFieldErrors(e));
    }
  };

  return (
    <Modal
      open
      title={isEdit ? t('tasks.editTitle') : t('tasks.newTitle')}
      onClose={onClose}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>{t('common.cancel')}</Button>
          <Button variant="primary" loading={create.isPending || update.isPending} onClick={submit}>
            {isEdit ? t('common.save') : t('common.create')}
          </Button>
        </>
      }
    >
      <div className="flex flex-col gap-4">
        <TextField label={t('tasks.titleField')} value={title} onChange={(e) => setTitle(e.target.value)} placeholder={t('tasks.titlePh')} error={fieldErrors.title ?? null} required />
        <TextAreaField label={t('common.descriptionOptional')} value={description} onChange={(e) => setDescription(e.target.value)} error={fieldErrors.description ?? null} />
        <div className="grid grid-cols-2 gap-3">
          <SelectInput
            label={t('common.status')}
            options={statusOptions}
            value={statusOptions.find((o) => o.value === status) ?? null}
            onChange={(next) => setStatus((next as SelectOption<TaskStatus> | null)?.value ?? 'TODO')}
          />
          <SelectInput
            label={t('tasks.priority')}
            options={priorityOptions}
            value={priorityOptions.find((o) => o.value === priority) ?? null}
            onChange={(next) => setPriority((next as SelectOption<TaskPriority> | null)?.value ?? 'MEDIUM')}
          />
        </div>
        <SelectInput
          label={t('tasks.assignee')}
          isClearable
          options={users.map((u) => ({ value: u.id, label: u.email }))}
          value={
            assigneeId
              ? { value: assigneeId, label: users.find((u) => u.id === assigneeId)?.email ?? assigneeId }
              : null
          }
          onChange={(next) => setAssigneeId((next as SelectOption | null)?.value ?? '')}
          placeholder={t('tasks.unassigned')}
        />
        <TextField label={t('tasks.dueDate')} type="date" value={dueDate} onChange={(e) => setDueDate(e.target.value)} error={fieldErrors.dueDate ?? null} />
      </div>
    </Modal>
  );
}
