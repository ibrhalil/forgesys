import type { InputHTMLAttributes, ReactNode } from 'react';
import { cn } from '../../lib/cn';

interface FieldProps {
  id?: string;
  label: string;
  error?: string | null;
  hint?: ReactNode;
  className?: string;
  children: ReactNode;
}

/** Shared label + error wrapper for form controls. */
export function Field({ id, label, error, hint, className, children }: FieldProps) {
  return (
    <div className={cn('flex flex-col gap-1.5', className)}>
      <label htmlFor={id} className="text-xs font-medium uppercase tracking-wide text-muted">
        {label}
      </label>
      {children}
      {hint && !error && <span className="text-xs text-muted/70">{hint}</span>}
      {error && <span className="text-xs text-danger">{error}</span>}
    </div>
  );
}

const INPUT_BASE =
  'w-full rounded-lg border bg-main/5 px-3 py-2 text-sm text-main placeholder:text-muted/50 ' +
  'transition-colors focus:outline-none focus:ring-2 focus:ring-accent/50';

interface TextFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string;
  error?: string | null;
  hint?: ReactNode;
}

export function TextField({ label, error, hint, id, className, ...rest }: TextFieldProps) {
  return (
    <Field id={id} label={label} error={error} hint={hint}>
      <input
        id={id}
        className={cn(INPUT_BASE, error ? 'border-danger/50' : 'border-glass', className)}
        {...rest}
      />
    </Field>
  );
}
