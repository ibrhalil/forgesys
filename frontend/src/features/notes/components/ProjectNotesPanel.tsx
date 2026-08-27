import { PERMISSIONS } from '../../../lib/permissions';
import { Link, useNavigate } from 'react-router-dom';
import { LuPin, LuPlus } from 'react-icons/lu';
import { useNotes } from '../hooks';
import { Badge } from '../../../components/ui/Badge';
import { Button } from '../../../components/ui/Button';
import { useT } from '../../../lib/i18n';
import { useAuthStore } from '../../../store/authStore';
import { formatDateTime } from '../../../lib/format';

/**
 * The NOTES-type project body (K-45): the project's notes (nested list, newest
 * first, single bounded page) with a create action that lands in the editor
 * pre-targeted at this container. Editing continues on the note pages.
 */
export function ProjectNotesPanel({ projectId }: { projectId: string }) {
  const { t } = useT();
  const navigate = useNavigate();
  const { data, isLoading } = useNotes({
    projectId,
    page: 0,
    size: 50,
    sorts: [{ field: 'updatedAt', dir: 'desc' }],
  });
  const canWrite = useAuthStore((s) => s.hasAuthority(PERMISSIONS.NOTE_WRITE));
  const notes = data?.items ?? [];

  return (
    <div className="rounded-lg border border-glass bg-surface">
      <div className="flex items-center justify-between gap-3 border-b border-glass px-5 py-3">
        <span className="text-sm font-medium text-main">{t('notes.inProject')}</span>
        {canWrite && (
          <Button variant="primary" size="sm" onClick={() => navigate(`/notes/new?projectId=${projectId}`)}>
            <LuPlus size={14} />
            {t('notes.new')}
          </Button>
        )}
      </div>

      {isLoading ? (
        <div className="px-5 py-12 text-center text-muted">{t('common.loading')}</div>
      ) : notes.length === 0 ? (
        <div className="px-5 py-12 text-center text-muted">{t('notes.emptyProject')}</div>
      ) : (
        <ul className="divide-y divide-glass">
          {notes.map((n) => (
            <li key={n.id} className="flex items-center gap-3 px-5 py-3">
              {n.pinned && <LuPin size={14} className="shrink-0 text-accent" aria-label={t('notes.pinned')} />}
              <Link
                to={`/notes/${n.id}`}
                className="min-w-0 flex-1 truncate font-medium text-main transition-colors hover:text-accent"
              >
                {n.title}
              </Link>
              {n.categoryName && <Badge tone="blue">{n.categoryName}</Badge>}
              <span className="shrink-0 text-xs text-muted">{formatDateTime(n.updatedAt)}</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
