import type { ReactNode } from 'react';
import { cn } from '../../lib/cn';

type Tone = 'accent' | 'blue' | 'green' | 'danger' | 'warning' | 'muted';

interface BadgeProps {
  tone?: Tone;
  /** Leading status dot (semantic state markers, e.g. active/suspended). */
  dot?: boolean;
  children: ReactNode;
  className?: string;
}

const TONES: Record<Tone, string> = {
  accent: 'bg-accent/15 text-accent',
  blue: 'bg-accent-blue/15 text-accent-blue',
  green: 'bg-accent-green/15 text-accent-green',
  danger: 'bg-danger/15 text-danger',
  warning: 'bg-warning/15 text-warning',
  muted: 'bg-main/5 text-muted',
};

const DOTS: Record<Tone, string> = {
  accent: 'bg-accent',
  blue: 'bg-accent-blue',
  green: 'bg-accent-green',
  danger: 'bg-danger',
  warning: 'bg-warning',
  muted: 'bg-muted/60',
};

/** Static status marker (K-54: squared, tint-only). Interactive control tags live in SelectInput. */
export function Badge({ tone = 'muted', dot = false, children, className }: BadgeProps) {
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 rounded px-2.5 py-0.5 text-xs font-medium',
        TONES[tone],
        className,
      )}
    >
      {dot && <span aria-hidden className={cn('h-1.5 w-1.5 rounded-full', DOTS[tone])} />}
      {children}
    </span>
  );
}
