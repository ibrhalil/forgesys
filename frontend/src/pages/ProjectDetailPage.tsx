import { Link, useParams } from 'react-router-dom';
import { useProject } from '../hooks/useProjects';
import { Badge } from '../components/ui/Badge';
import { TaskBoard } from '../components/TaskBoard';

/**
 * Project workspace. Renders the project header; a TASKS-type project shows the task
 * board, other types show a "coming soon" placeholder.
 */
export function ProjectDetailPage() {
  const { projectId } = useParams<{ projectId: string }>();
  const { data: project, isLoading } = useProject(projectId);

  if (isLoading) {
    return <div className="py-10 text-center text-sm text-muted">Loading project…</div>;
  }
  if (!project) {
    return (
      <div className="flex flex-col items-center gap-3 py-16 text-center text-muted">
        <p>Project not found.</p>
        <Link to="/" className="text-accent hover:underline">Back to projects</Link>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-8">
      <header className="flex flex-col gap-3">
        <Link to="/" className="text-sm text-muted transition-colors hover:text-accent">← All projects</Link>
        <div className="flex flex-wrap items-center gap-3">
          <h1 className="m-0 text-3xl font-semibold tracking-tight text-white">{project.name}</h1>
          <Badge tone={project.type === 'TASKS' ? 'accent' : 'blue'}>{project.type}</Badge>
        </div>
        {project.description && <p className="m-0 text-sm text-muted">{project.description}</p>}
      </header>

      {project.type === 'TASKS' ? (
        <TaskBoard projectId={project.id} />
      ) : (
        <div className="rounded-xl border border-glass bg-surface px-6 py-16 text-center text-muted">
          The <span className="text-main">{project.type}</span> module is coming soon.
        </div>
      )}
    </div>
  );
}
