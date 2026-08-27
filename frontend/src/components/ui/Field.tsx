import type { InputHTMLAttributes, ReactNode } from 'react';
import { cn } from '../../lib/cn';
import { INPUT_BASE, MICRO_LABEL } from './styles';

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
      <label htmlFor={id} className={MICRO_LABEL}>
        {label}
      </label>
      {children}
      {hint && !error && <span className="text-xs text-muted/70">{hint}</span>}
      {error && <span className="text-xs text-danger">{error}</span>}
    </div>
  );
}

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
        className={cn(INPUT_BASE, error ? 'border-danger/60' : 'border-glass', className)}
        {...rest}
      />
    </Field>
  );
}
