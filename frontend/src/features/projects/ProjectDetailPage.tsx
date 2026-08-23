import { useParams } from 'react-router-dom';
import { useProject, useProjectTypeLabels } from './hooks';
import { Badge } from '../../components/ui/Badge';
import { Page } from '../../components/Page';
import { TaskBoard } from './components/TaskBoard';
import { ProjectNotesPanel } from '../notes/components/ProjectNotesPanel';
import { ProjectAppsPanel } from '../apps/components/ProjectAppsPanel';
import { useT } from '../../lib/i18n';
import { DetailLoading, DetailNotFound } from '../../components/detail/DetailFallback';

/**
 * Typed project container (K-45): the type decides the body — TASKS renders the task
 * board, NOTES the project's notes, APPS the project's custom app collection. Unknown
 * future types fall back to the "coming soon" placeholder.
 */
export function ProjectDetailPage() {
  const { t } = useT();
  const { projectId } = useParams<{ projectId: string }>();
  const { data: project, isLoading } = useProject(projectId);
  const typeLabels = useProjectTypeLabels();

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
          <Badge tone={project.type === 'TASKS' ? 'accent' : project.type === 'NOTES' ? 'blue' : 'green'}>
            {typeLabels[project.type]}
          </Badge>
        </span>
      )}
      description={project.description ?? undefined}
    >

      {project.type === 'TASKS' ? (
        <TaskBoard projectId={project.id} />
      ) : project.type === 'NOTES' ? (
        <ProjectNotesPanel projectId={project.id} />
      ) : project.type === 'APPS' ? (
        <ProjectAppsPanel projectId={project.id} />
      ) : (
        <div className="rounded-xl border border-glass bg-surface px-6 py-16 text-center text-muted">
          {t('projects.comingSoon', { type: typeLabels[project.type] })}
        </div>
      )}
    </Page>
  );
}
