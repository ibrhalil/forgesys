import { PERMISSIONS } from '../../lib/permissions';
import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import Markdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { useNote, useNoteCategories, useCreateNote, useUpdateNote, useDeleteNote } from './hooks';
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
 * Note editor (K-44): title + category + pinned + markdown content with a
 * write/preview toggle. New notes land here after the backend create (PUT-shaped
 * local draft until the first save creates the note, then the URL becomes the
 * note's own). Raw HTML is never rendered — react-markdown without rehype-raw
 * ignores it by design, so the preview cannot inject markup.
 */
export function NoteEditorPage() {
  const { t } = useT();
  const navigate = useNavigate();
  const { noteId } = useParams<{ noteId: string }>();
  const isNew = noteId === 'new';
  const { data: note, isLoading } = useNote(isNew ? undefined : noteId);
  const { data: categories } = useNoteCategories();
  const update = useUpdateNote();
  const create = useCreateNote();
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

  const categoryOptions = (categories?.items ?? []).map((c: NoteCategory) => ({ value: c.id, label: c.name }));
  const saving = create.isPending || update.isPending;

  const save = async () => {
    setFieldErrors({});
    try {
      if (isNew) {
        const created = await create.mutateAsync({ title, content, categoryId, pinned });
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
            <SelectInput
              className="w-48"
              label={t('notes.categoryCol')}
              placeholder={t('notes.uncategorized')}
              isClearable
              options={categoryOptions}
              value={categoryId ? categoryOptions.find((o) => o.value === categoryId) ?? null : null}
              onChange={(next) => setCategoryId((next as { value: string } | null)?.value ?? null)}
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
