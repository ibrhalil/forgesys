import { useMemo, useState } from 'react';
import type { Task, TaskPriority, TaskStatus } from '../types';
import { useUsers } from '../hooks/useRbac';
import { useTasks, useCreateTask, useUpdateTask, useDeleteTask } from '../hooks/useProjects';
import { ApiError } from '../lib/api';
import { Modal } from './ui/Modal';
import { ConfirmDialog } from './ui/ConfirmDialog';
import { Button } from './ui/Button';
import { Badge } from './ui/Badge';
import { TextField } from './ui/Field';
import { TextAreaField } from './ui/TextArea';
import { SelectField } from './ui/Select';

const COLUMNS: { status: TaskStatus; label: string; tone: 'muted' | 'blue' | 'green' }[] = [
  { status: 'TODO', label: 'To Do', tone: 'muted' },
  { status: 'IN_PROGRESS', label: 'In Progress', tone: 'blue' },
  { status: 'DONE', label: 'Done', tone: 'green' },
];

const PRIORITY_TONE: Record<TaskPriority, 'danger' | 'warning' | 'muted'> = {
  HIGH: 'danger',
  MEDIUM: 'warning',
  LOW: 'muted',
};

function toRequest(task: Task, overrides: Partial<TaskRequestLike> = {}): TaskRequestLike {
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

type TaskRequestLike = {
  title: string;
  description?: string;
  status?: TaskStatus;
  priority?: TaskPriority;
  assigneeId?: string | null;
  dueDate?: string | null;
};

/**
 * Three-column task board (TODO/IN_PROGRESS/DONE). No drag-drop — a card carries its own
 * status select so a user moves it across columns by changing the value (optimistic via
 * invalidate). Tasks are fetched as one list and grouped client-side.
 */
export function TaskBoard({ projectId }: { projectId: string }) {
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
        <h2 className="m-0 text-xl font-semibold text-white">Tasks</h2>
        <Button variant="primary" onClick={() => setCreating(true)}>+ New Task</Button>
      </header>

      <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
        {COLUMNS.map((col) => {
          const colTasks = (tasks ?? []).filter((t) => t.status === col.status);
          return (
            <section key={col.status} className="flex min-h-[12rem] flex-col gap-3 rounded-xl border border-glass bg-surface/40 p-3">
              <div className="flex items-center justify-between px-1">
                <div className="flex items-center gap-2">
                  <Badge tone={col.tone}>{col.label}</Badge>
                </div>
                <span className="text-xs text-muted">{colTasks.length}</span>
              </div>

              {isLoading ? (
                <div className="py-6 text-center text-xs text-muted">Loading…</div>
              ) : colTasks.length === 0 ? (
                <div className="rounded-lg border border-dashed border-glass px-3 py-6 text-center text-xs text-muted">No tasks</div>
              ) : (
                <div className="flex flex-col gap-2">
                  {colTasks.map((task) => (
                    <TaskCard
                      key={task.id}
                      task={task}
                      projectId={projectId}
                      usersById={usersById}
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
        title="Delete task"
        message={`Delete "${deleting?.title}"?`}
        confirmText="Delete"
        danger
        loading={delTask.isPending}
        onConfirm={async () => {
          if (!deleting) return;
          try {
            await delTask.mutateAsync({ projectId, taskId: deleting.id });
            setDeleting(null);
          } catch {
            /* noop */
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
  onEdit,
  onDelete,
}: {
  task: Task;
  projectId: string;
  usersById: Map<string, string>;
  onEdit: () => void;
  onDelete: () => void;
}) {
  const updateTask = useUpdateTask();
  const move = (status: TaskStatus) =>
    updateTask.mutate({ projectId, taskId: task.id, data: toRequest(task, { status }) });

  return (
    <article className="flex flex-col gap-2 rounded-lg border border-glass bg-bg/40 p-3">
      <div className="flex items-start justify-between gap-2">
        <span className="font-medium text-main">{task.title}</span>
        <Badge tone={PRIORITY_TONE[task.priority]}>{task.priority}</Badge>
      </div>
      {task.description && <p className="line-clamp-2 text-xs text-muted">{task.description}</p>}
      <div className="flex items-center justify-between text-xs text-muted">
        <span className="truncate">
          {task.assigneeId ? usersById.get(task.assigneeId) ?? 'Unassigned' : 'Unassigned'}
        </span>
        {task.dueDate && <span>{task.dueDate}</span>}
      </div>
      <div className="flex items-center gap-2 border-t border-glass pt-2">
        <select
          value={task.status}
          onChange={(e) => move(e.target.value as TaskStatus)}
          disabled={updateTask.isPending}
          className="rounded-md border border-glass bg-white/5 px-2 py-1 text-xs text-main focus:outline-none focus:ring-2 focus:ring-accent/50"
        >
          <option value="TODO">To Do</option>
          <option value="IN_PROGRESS">In Progress</option>
          <option value="DONE">Done</option>
        </select>
        <button onClick={onEdit} className="ml-auto text-xs text-muted transition-colors hover:text-accent">Edit</button>
        <button onClick={onDelete} className="text-xs text-muted transition-colors hover:text-danger">Delete</button>
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
  const create = useCreateTask();
  const update = useUpdateTask();
  const isEdit = !!task;

  const [title, setTitle] = useState(task?.title ?? '');
  const [description, setDescription] = useState(task?.description ?? '');
  const [status, setStatus] = useState<TaskStatus>(task?.status ?? 'TODO');
  const [priority, setPriority] = useState<TaskPriority>(task?.priority ?? 'MEDIUM');
  const [assigneeId, setAssigneeId] = useState<string>(task?.assigneeId ?? '');
  const [dueDate, setDueDate] = useState<string>(task?.dueDate ?? '');
  const [error, setError] = useState<string | null>(null);

  const submit = async () => {
    setError(null);
    const data: TaskRequestLike = {
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
      } else {
        await create.mutateAsync({ projectId, data });
      }
      onClose();
    } catch (e) {
      setError(e instanceof ApiError ? e.body.message : 'Could not save task');
    }
  };

  return (
    <Modal
      open
      title={isEdit ? 'Edit Task' : 'New Task'}
      onClose={onClose}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>Cancel</Button>
          <Button variant="primary" loading={create.isPending || update.isPending} onClick={submit}>
            {isEdit ? 'Save' : 'Create'}
          </Button>
        </>
      }
    >
      <div className="flex flex-col gap-4">
        <TextField label="Title" value={title} onChange={(e) => setTitle(e.target.value)} placeholder="What needs doing?" required />
        <TextAreaField label="Description (optional)" value={description} onChange={(e) => setDescription(e.target.value)} />
        <div className="grid grid-cols-2 gap-3">
          <SelectField label="Status" value={status} onChange={(e) => setStatus(e.target.value as TaskStatus)}>
            <option value="TODO">To Do</option>
            <option value="IN_PROGRESS">In Progress</option>
            <option value="DONE">Done</option>
          </SelectField>
          <SelectField label="Priority" value={priority} onChange={(e) => setPriority(e.target.value as TaskPriority)}>
            <option value="LOW">Low</option>
            <option value="MEDIUM">Medium</option>
            <option value="HIGH">High</option>
          </SelectField>
        </div>
        <SelectField label="Assignee (optional)" value={assigneeId} onChange={(e) => setAssigneeId(e.target.value)}>
          <option value="">Unassigned</option>
          {users.map((u) => (
            <option key={u.id} value={u.id}>{u.email}</option>
          ))}
        </SelectField>
        <TextField label="Due date (optional)" type="date" value={dueDate} onChange={(e) => setDueDate(e.target.value)} />
        {error && <div className="rounded-lg border border-danger/30 bg-danger/10 px-3 py-2 text-sm text-danger">{error}</div>}
      </div>
    </Modal>
  );
}
