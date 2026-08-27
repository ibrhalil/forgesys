import { LuFolderOpen } from 'react-icons/lu';
import type { IconType } from 'react-icons';
import { useT } from '../../lib/i18n';

interface EmptyStateProps {
  message?: string;
  hint?: string;
  /** Context icon; defaults to the generic open-folder. */
  icon?: IconType;
}

export function EmptyState({ message, hint, icon: Icon = LuFolderOpen }: EmptyStateProps) {
  const { t } = useT();
  return (
    <div className="flex flex-col items-center justify-center gap-2 py-16 text-center">
      <Icon size={40} strokeWidth={1.5} className="text-muted/50" aria-hidden />
      <p className="text-sm text-muted">{message ?? t('table.noData')}</p>
      {hint && <p className="text-xs text-muted/60">{hint}</p>}
    </div>
  );
}
