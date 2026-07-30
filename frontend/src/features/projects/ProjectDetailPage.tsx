import { useParams } from 'react-router-dom';
import type { ProjectType } from './types';
import { useProject } from './hooks';
import { Badge } from '../../components/ui/Badge';
import { Page } from '../../components/Page';
import { TaskBoard } from './components/TaskBoard';
import { useT } from '../../lib/i18n';
import { DetailLoading, DetailNotFound } from '../../components/detail/DetailFallback';

/**
 * Project workspace. Renders the project header; a TASKS-type project shows the task
 * board, other types show a "coming soon" placeholder.
 */
export function ProjectDetailPage() {
  const { t } = useT();
  const { projectId } = useParams<{ projectId: string }>();
  const { data: project, isLoading } = useProject(projectId);
  // Label keys mirror ProjectsPage's useTypeOptions — the localized name for badges/placeholders.
  const typeLabels: Record<ProjectType, string> = {
    TASKS: t('projects.typeTasks'),
    NOTES: t('projects.typeNotes'),
  };

  if (isLoading) {
    return <DetailLoading message={t('projects.loadingProject')} />;
  }
  if (!project) {
    return <DetailNotFound message={t('projects.notFound')} backLabel={t('projects.backToProjects')} backTo="/" />;
  }

  return (
    <Page
      breadcrumb={[{ label: t('nav.projects'), to: '/' }, { label: project.name }]}
      title={(
        <span className="flex flex-wrap items-center gap-3">
          <span className="truncate">{project.name}</span>
          <Badge tone={project.type === 'TASKS' ? 'accent' : 'blue'}>{typeLabels[project.type]}</Badge>
        </span>
      )}
      description={project.description ?? undefined}
    >

      {project.type === 'TASKS' ? (
        <TaskBoard projectId={project.id} />
      ) : (
        <div className="rounded-xl border border-glass bg-surface px-6 py-16 text-center text-muted">
          {t('projects.comingSoon', { type: typeLabels[project.type] })}
        </div>
      )}
    </Page>
  );
}
