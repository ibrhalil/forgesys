import { useId } from 'react';
import { LuSearch, LuX } from 'react-icons/lu';
import { cn } from '../../lib/cn';
import { useT } from '../../lib/i18n';

interface SearchInputProps {
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  className?: string;
}

/** Toolbar search box for server-side (`q`) list filtering. */
export function SearchInput({ value, onChange, placeholder, className }: SearchInputProps) {
  const { t } = useT();
  const inputId = useId();

  return (
    <div className={cn('relative', className)}>
      <LuSearch
        aria-hidden
        strokeWidth={1.5}
        className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted"
      />
      <input
        id={inputId}
        type="search"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        className="h-9 w-64 rounded-lg border border-glass bg-surface px-9 text-sm text-main placeholder:text-muted focus:border-accent/40 focus:outline-none focus:ring-2 focus:ring-accent/15"
      />
      {value && (
        <button
          type="button"
          aria-label={t('common.clear')}
          onClick={() => onChange('')}
          className="absolute right-2.5 top-1/2 -translate-y-1/2 rounded-full p-0.5 text-xs text-muted transition-colors hover:text-main"
        >
          <LuX className="h-3.5 w-3.5" aria-hidden />
        </button>
      )}
    </div>
  );
}
