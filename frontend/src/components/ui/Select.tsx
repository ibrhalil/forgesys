import type { SelectHTMLAttributes, ReactNode } from 'react';
import { Field } from './Field';
import { cn } from '../../lib/cn';

interface SelectFieldProps extends SelectHTMLAttributes<HTMLSelectElement> {
  label: string;
  error?: string | null;
  hint?: ReactNode;
  children: ReactNode;
}

export function SelectField({ label, error, hint, id, className, children, ...rest }: SelectFieldProps) {
  return (
    <Field id={id} label={label} error={error} hint={hint}>
      <select
        id={id}
        className={cn(
          'w-full rounded-lg border bg-white/5 px-3 py-2 text-sm text-main transition-colors',
          'focus:outline-none focus:ring-2 focus:ring-accent/50',
          error ? 'border-danger/50' : 'border-glass',
          className,
        )}
        {...rest}
      >
        {children}
      </select>
    </Field>
  );
}
