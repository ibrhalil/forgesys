import type { ReactNode } from 'react';
import { cn } from '../../lib/cn';

type Tone = 'accent' | 'blue' | 'green' | 'danger' | 'warning' | 'muted';

interface BadgeProps {
  tone?: Tone;
  children: ReactNode;
  className?: string;
}

const TONES: Record<Tone, string> = {
  accent: 'bg-accent/15 text-accent border border-accent/30',
  blue: 'bg-accent-blue/15 text-accent-blue border border-accent-blue/30',
  green: 'bg-accent-green/15 text-accent-green border border-accent-green/30',
  danger: 'bg-danger/15 text-danger border border-danger/30',
  warning: 'bg-warning/15 text-warning border border-warning/30',
  muted: 'bg-white/5 text-muted border border-glass',
};

export function Badge({ tone = 'muted', children, className }: BadgeProps) {
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-medium',
        TONES[tone],
        className,
      )}
    >
      {children}
    </span>
  );
}
