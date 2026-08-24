import { PERMISSIONS } from '../../lib/permissions';
import { useEffect, useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import Markdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { useNote, useNoteCategories, useCreateNote, useUpdateNote, useDeleteNote, useCreateNoteCategory } from './hooks';
import { useProjectTypes, useProjects } from '../projects/hooks';
import type { NoteCategory } from './types';
import { notify, extractFieldErrors } from '../../lib/notify';
import { LuEye, LuPin, LuSquarePen, LuTrash2 } from 'react-icons/lu';
import { Button } from '../../components/ui/Button';
import { Page } from '../../components/Page';
import { Badge } from '../../components/ui/Badge';
import { TextField } from '../../components/ui/Field';
import { TextAreaField } from '../../components/ui/TextArea';
import { SelectInput } from '../../components/ui/SelectInput';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { RowMenu } from '../../components/ui/RowMenu';
import { useT } from '../../lib/i18n';
import { useAuthStore } from '../../store/authStore';

/**
 * Note editor (K-44, re-scoped by K-45): title + target project + category + pinned +
 * markdown content with a write/preview toggle. New notes carry the chosen project
 * (defaulted from the catalog's default NOTES container or the ?projectId= param that
 * the project panel passes); editing keeps the note's container fixed. Raw HTML is
 * never rendered — react-markdown without rehype-raw ignores it by design, so the
 * preview cannot inject markup.
 */
export function NoteEditorPage() {
  const { t } = useT();
  const navigate = useNavigate();
  const { noteId } = useParams<{ noteId: string }>();
  const isNew = noteId === 'new';
  const [searchParams] = useSearchParams();
  const { data: note, isLoading } = useNote(isNew ? undefined : noteId);
  const { data: typeCatalog } = useProjectTypes();
  const defaultNotesProjectId = typeCatalog?.find((c) => c.type === 'NOTES')?.defaultProjectId ?? null;
  const [projectId, setProjectId] = useState<string | null>(searchParams.get('projectId'));
  const { data: projects } = useProjects(
    { page: 0, size: 100, sorts: [{ field: 'name', dir: 'asc' }], type: 'NOTES' },
    isNew,
  );
  const currentProjectId = isNew ? projectId : note?.projectId;
  const { data: categories } = useNoteCategories(currentProjectId ?? undefined);
  const update = useUpdateNote();
  const create = useCreateNote();
  const createCategory = useCreateNoteCategory();
  const delNote = useDeleteNote();
  const canWrite = useAuthStore((s) => s.hasAuthority(PERMISSIONS.NOTE_WRITE));
  const canDelete = useAuthStore((s) => s.hasAuthority(PERMISSIONS.NOTE_DELETE));

  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [categoryId, setCategoryId] = useState<string | null>(null);
  const [pinned, setPinned] = useState(false);
  const [preview, setPreview] = useState(false);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [deleting, setDeleting] = useState(false);

  useEffect(() => {
    if (note) {
      setTitle(note.title);
      setContent(note.content);
      setCategoryId(note.categoryId);
      setPinned(note.pinned);
    }
  }, [note]);

  // Late catalog resolve: ?projectId= absent → default NOTES container once known.
  useEffect(() => {
    if (isNew && !projectId && defaultNotesProjectId) {
      setProjectId(defaultNotesProjectId);
    }
  }, [isNew, projectId, defaultNotesProjectId]);

  const projectOptions = (projects?.items ?? []).map((p) => ({ value: p.id, label: p.name }));
  const categoryOptions = (categories?.items ?? []).map((c: NoteCategory) => ({ value: c.id, label: c.name }));
  const saving = create.isPending || update.isPending;

  const handleCategoryChange = async (next: { value: string; label: string } | { value: string; label: string }[] | null) => {
    if (!next) {
      setCategoryId(null);
      return;
    }
    const option = Array.isArray(next) ? next[0] : next;
    // Check if this is a new category (not in existing options)
    const exists = categoryOptions.some((o) => o.value === option.value);
    if (exists) {
      setCategoryId(option.value);
      return;
    }
    // Create new category in the current project
    if (!currentProjectId) {
      notify.error(t('notes.categoryCreateNoProject'));
      return;
    }
    try {
      const created = await createCategory.mutateAsync({ name: option.label, projectId: currentProjectId });
      setCategoryId(created.id);
    } catch (e) {
      setFieldErrors(extractFieldErrors(e));
    }
  };

  const save = async () => {
    setFieldErrors({});
    try {
      if (isNew) {
        const created = await create.mutateAsync({
          title,
          content,
          categoryId,
          pinned,
          projectId: projectId ?? undefined,
        });
        notify.success(t('notes.saved'));
        navigate(`/notes/${created.id}`, { replace: true });
      } else {
        await update.mutateAsync({ id: noteId as string, data: { title, content, categoryId, pinned } });
        notify.success(t('notes.saved'));
      }
    } catch (e) {
      setFieldErrors(extractFieldErrors(e));
    }
  };

  return (
    <Page
      breadcrumb={[{ label: t('nav.notes'), to: '/notes' }, { label: isNew ? t('notes.new') : title || t('notes.note') }]}
      title={isNew ? t('notes.newTitle') : title || t('notes.note')}
      description={note?.categoryName ? undefined : t('notes.editorDesc')}
      actions={
        canWrite ? (
          <div className="flex items-center gap-3">
            <Button variant="primary" loading={saving} onClick={save}>
              {t('common.save')}
            </Button>
            <RowMenu
              ariaLabel={t('common.actions')}
              items={[
                {
                  label: pinned ? t('notes.unpin') : t('notes.pin'),
                  icon: LuPin,
                  onClick: () => setPinned((v) => !v),
                },
                ...(canDelete && !isNew
                  ? [{ label: t('common.delete'), icon: LuTrash2, danger: true, onClick: () => setDeleting(true) }]
                  : []),
              ]}
            />
          </div>
        ) : undefined
      }
    >
      {isLoading && !isNew ? (
        <div className="py-16 text-center text-muted">{t('common.loading')}</div>
      ) : (
        <div className="flex flex-col gap-4">
          <div className="flex flex-wrap items-end gap-3">
            <div className="min-w-64 flex-1">
              <TextField
                label={t('notes.titleCol')}
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                placeholder={t('notes.titlePh')}
                error={fieldErrors.title ?? null}
                required
                disabled={!canWrite}
              />
            </div>
            {isNew ? (
              <SelectInput
                className="w-48"
                label={t('projects.project')}
                placeholder={t('common.loading')}
                options={projectOptions}
                value={projectOptions.find((o) => o.value === projectId) ?? null}
                onChange={(next) => {
                  setProjectId((next as { value: string } | null)?.value ?? null);
                  // Categories are per-container — a stale selection must not survive.
                  setCategoryId(null);
                }}
              />
            ) : (
              note?.projectName && (
                <div className="pb-1.5">
                  <span className="text-xs font-medium uppercase tracking-wide text-muted">{t('projects.project')}</span>
                  <div className="mt-1.5">
                    <Badge tone="blue">{note.projectName}</Badge>
                  </div>
                </div>
              )
            )}
            <SelectInput
              className="w-48"
              label={t('notes.categoryCol')}
              placeholder={t('notes.uncategorized')}
              isClearable
              creatable
              options={categoryOptions}
              value={categoryId ? categoryOptions.find((o) => o.value === categoryId) ?? null : null}
              onChange={handleCategoryChange}
              formatCreateLabel={(input) => t('notes.createCategory', { name: input })}
            />
            {pinned && <Badge tone="accent"><LuPin size={12} className="mr-1 inline" />{t('notes.pinned')}</Badge>}
          </div>

          <div className="flex items-center justify-end">
            <Button variant="ghost" size="sm" onClick={() => setPreview((v) => !v)}>
              {preview ? <LuSquarePen size={14} /> : <LuEye size={14} />}
              {preview ? t('notes.editMode') : t('notes.previewMode')}
            </Button>
          </div>

          {preview ? (
            <div className="markdown-body min-h-64 rounded-xl border border-glass bg-surface p-5">
              <Markdown remarkPlugins={[remarkGfm]}>{content || t('notes.previewEmpty')}</Markdown>
            </div>
          ) : (
            <TextAreaField
              label={t('notes.content')}
              value={content}
              onChange={(e) => setContent(e.target.value)}
              placeholder={t('notes.contentPh')}
              error={fieldErrors.content ?? null}
              disabled={!canWrite}
              rows={16}
              className="font-mono text-sm"
            />
          )}
        </div>
      )}

      <ConfirmDialog
        open={deleting}
        title={t('notes.deleteTitle')}
        message={t('notes.deleteMsg', { name: title })}
        confirmText={t('common.delete')}
        danger
        loading={delNote.isPending}
        onConfirm={async () => {
          if (!noteId || isNew) return;
          try {
            await delNote.mutateAsync(noteId);
            notify.success(t('notes.deleted'));
            navigate('/notes', { replace: true });
          } catch {
            /* global toast */
          }
        }}
        onClose={() => setDeleting(false)}
      />
    </Page>
  );
}
