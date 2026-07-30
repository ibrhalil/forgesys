import type { TextareaHTMLAttributes, ReactNode } from 'react';
import { Field } from './Field';
import { cn } from '../../lib/cn';

interface TextAreaFieldProps extends TextareaHTMLAttributes<HTMLTextAreaElement> {
  label: string;
  error?: string | null;
  hint?: ReactNode;
}

export function TextAreaField({ label, error, hint, id, className, ...rest }: TextAreaFieldProps) {
  return (
    <Field id={id} label={label} error={error} hint={hint}>
      <textarea
        id={id}
        className={cn(
          'w-full rounded-lg border bg-main/5 px-3 py-2 text-sm text-main placeholder:text-muted/50',
          'transition-colors focus:outline-none focus:ring-2 focus:ring-accent/50 resize-y min-h-[80px]',
          error ? 'border-danger/50' : 'border-glass',
          className,
        )}
        {...rest}
      />
    </Field>
  );
}
