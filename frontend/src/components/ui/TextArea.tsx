import type { TextareaHTMLAttributes, ReactNode } from 'react';
import { Field } from './Field';
import { cn } from '../../lib/cn';
import { INPUT_BASE } from './styles';

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
          INPUT_BASE,
          'resize-y min-h-[80px]',
          error ? 'border-danger/60' : 'border-glass',
          className,
        )}
        {...rest}
      />
    </Field>
  );
}
