import { LuFolderOpen } from 'react-icons/lu';
import { useT } from '../../lib/i18n';

interface EmptyStateProps {
  message?: string;
  hint?: string;
}

export function EmptyState({ message, hint }: EmptyStateProps) {
  const { t } = useT();
  return (
    <div className="flex flex-col items-center justify-center gap-2 py-16 text-center">
      <LuFolderOpen size={40} strokeWidth={1.5} className="text-muted/50" />
      <p className="text-sm text-muted">{message ?? t('table.noData')}</p>
      {hint && <p className="text-xs text-muted/60">{hint}</p>}
    </div>
  );
}
