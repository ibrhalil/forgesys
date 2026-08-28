import { useState } from 'react';
import { LuCheck, LuCopy } from 'react-icons/lu';
import { cn } from '../../lib/cn';
import { useT } from '../../lib/i18n';

interface CopyableValueProps {
  value: string;
  className?: string;
  /** What is being copied — feeds the button label/title (e.g. "trace id"). */
  label?: string;
}

/**
 * Mono value with a one-click copy affordance (K-55 F3) — trace ids, UUIDs and
 * other machine data that users paste into searches or bug reports.
 */
export function CopyableValue({ value, className, label }: CopyableValueProps) {
  const { t } = useT();
  const [copied, setCopied] = useState(false);

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(value);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch {
      // Clipboard unavailable (insecure context / permissions) — the raw value stays selectable.
    }
  };

  const action = t('common.copy');

  return (
    <span className={cn('inline-flex min-w-0 items-center gap-1.5', className)}>
      <span className="truncate font-mono text-xs text-muted" title={value}>{value}</span>
      <button
        type="button"
        onClick={copy}
        aria-label={label ? `${action}: ${label}` : action}
        title={action}
        className="shrink-0 rounded p-0.5 text-muted/60 transition-colors hover:bg-main/5 hover:text-main focus:outline-none focus-visible:ring-2 focus-visible:ring-accent/60"
      >
        {copied ? <LuCheck size={12} className="text-accent" aria-hidden /> : <LuCopy size={12} aria-hidden />}
      </button>
    </span>
  );
}
