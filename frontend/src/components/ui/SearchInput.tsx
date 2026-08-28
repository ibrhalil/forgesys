import { useEffect, useId, useRef, useState } from 'react';
import { LuFilter, LuSearch, LuX } from 'react-icons/lu';
import { cn } from '../../lib/cn';
import { useT } from '../../lib/i18n';
import { loadTablePreferences, saveTablePreferences } from '../../lib/tablePreferences';
import { Badge } from './Badge';

export interface SearchFieldOption {
  key: string;
  label: string;
  /**
   * Whether this column supports text searching.
   * If false, rendered as disabled with a "Not supported" badge in the selector.
   * Defaults to true.
   */
  searchable?: boolean;
}

export interface SearchInputProps {
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  className?: string;
  /**
   * Available fields for targeted smart search.
   * If provided, renders an integrated column filter button.
   */
  fields?: SearchFieldOption[];
  /**
   * Currently active search field keys. Empty array indicates 'all fields'.
   */
  selectedFields?: string[];
  /**
   * Callback when selected search fields change.
   */
  onSelectedFieldsChange?: (fields: string[]) => void;
  /**
   * Optional storage key to persist search field preferences in localStorage.
   */
  storageKey?: string;
}

/** Toolbar search box with optional smart column targeting for server-side (`q`) list filtering. */
export function SearchInput({
  value,
  onChange,
  placeholder,
  className,
  fields,
  selectedFields: controlledSelectedFields,
  onSelectedFieldsChange,
  storageKey,
}: SearchInputProps) {
  const { t } = useT();
  const inputId = useId();

  const [internalSelectedFields, setInternalSelectedFields] = useState<string[]>(() => {
    if (!storageKey) return [];
    return loadTablePreferences(storageKey).searchFields ?? [];
  });

  const selectedFields = controlledSelectedFields ?? internalSelectedFields;
  const [open, setOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  const searchableFields = fields?.filter((f) => f.searchable !== false) ?? [];
  const isAllSelected = selectedFields.length === 0 || selectedFields.length === searchableFields.length;
  const hasActiveFilter = !isAllSelected && selectedFields.length > 0;

  // Close popover on click outside or Escape key
  useEffect(() => {
    if (!open) return;

    const onPointerDown = (e: MouseEvent | TouchEvent) => {
      const target = e.target as Node;
      if (containerRef.current && !containerRef.current.contains(target)) {
        setOpen(false);
      }
    };
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false);
    };

    document.addEventListener('mousedown', onPointerDown);
    document.addEventListener('touchstart', onPointerDown);
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('mousedown', onPointerDown);
      document.removeEventListener('touchstart', onPointerDown);
      document.removeEventListener('keydown', onKeyDown);
    };
  }, [open]);

  const updateSelectedFields = (next: string[]) => {
    if (onSelectedFieldsChange) {
      onSelectedFieldsChange(next);
    } else {
      setInternalSelectedFields(next);
    }
    if (storageKey) {
      saveTablePreferences(storageKey, { searchFields: next });
    }
  };

  const toggleField = (fieldKey: string) => {
    if (isAllSelected) {
      // Transition from "all" to just this single field
      updateSelectedFields([fieldKey]);
      return;
    }

    const exists = selectedFields.includes(fieldKey);
    let next: string[];
    if (exists) {
      next = selectedFields.filter((k) => k !== fieldKey);
    } else {
      next = [...selectedFields, fieldKey];
    }

    // If everything is selected or nothing is selected, treat as "all"
    if (next.length === searchableFields.length || next.length === 0) {
      updateSelectedFields([]);
    } else {
      updateSelectedFields(next);
    }
  };

  const handleSelectAll = () => {
    updateSelectedFields([]);
  };

  return (
    <div ref={containerRef} className={cn('relative inline-flex items-center', className)}>
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
        maxLength={200} // mirrors the backend SearchRequest q cap (@Size(max=200))
        placeholder={
          placeholder ??
          (hasActiveFilter
            ? t('search.activeCount', { count: selectedFields.length })
            : undefined)
        }
        className={cn(
          'h-9 w-64 rounded-md border border-glass bg-surface pl-9 text-sm text-main placeholder:text-muted/50 focus:outline-none focus:ring-2 focus:ring-accent/50',
          fields && value ? 'pr-16' : fields || value ? 'pr-9' : 'pr-3',
        )}
      />

      <div className="absolute right-2 top-1/2 flex -translate-y-1/2 items-center gap-1">
        {value && (
          <button
            type="button"
            aria-label={t('common.clear')}
            onClick={() => onChange('')}
            className="rounded-full p-0.5 text-muted transition-colors hover:text-main"
          >
            <LuX className="h-3.5 w-3.5" aria-hidden />
          </button>
        )}

        {fields && fields.length > 0 && (
          <button
            type="button"
            onClick={() => setOpen((v) => !v)}
            title={t('search.fields')}
            aria-label={t('search.fields')}
            aria-expanded={open}
            className={cn(
              'relative inline-flex h-6 w-6 items-center justify-center rounded-md text-muted transition-colors hover:bg-main/5 hover:text-main focus:outline-none focus-visible:ring-2 focus-visible:ring-accent/60',
              open && 'bg-main/10 text-main',
              hasActiveFilter && 'text-accent',
            )}
          >
            <LuFilter className="h-3.5 w-3.5" aria-hidden />
            {hasActiveFilter && (
              <span className="absolute -right-0.5 -top-0.5 h-1.5 w-1.5 rounded-full bg-accent" />
            )}
          </button>
        )}
      </div>

      {open && fields && fields.length > 0 && (
        <div className="absolute right-0 top-full z-60 mt-1.5 w-64 overflow-hidden rounded-lg border border-glass bg-surface p-2 shadow-lg shadow-black/10">
          <div className="mb-2 flex items-center justify-between border-b border-glass px-1 pb-1.5">
            <span className="text-xs font-semibold text-main">{t('search.fields')}</span>
            {hasActiveFilter && (
              <button
                type="button"
                onClick={handleSelectAll}
                className="text-[11px] font-medium text-accent hover:underline"
              >
                {t('search.reset')}
              </button>
            )}
          </div>

          <div className="space-y-1">
            {/* All Fields Option */}
            <label className="flex cursor-pointer select-none items-center gap-2 rounded-md px-2 py-1 text-xs transition-colors hover:bg-main/5">
              <input
                type="checkbox"
                checked={isAllSelected}
                onChange={handleSelectAll}
                className="accent-accent"
              />
              <span className="font-medium text-main">{t('search.allFields')}</span>
            </label>

            <div className="my-1 border-t border-glass/60" />

            {/* Individual Columns */}
            <div className="max-h-48 space-y-0.5 overflow-y-auto pr-1">
              {fields.map((field) => {
                const isSearchable = field.searchable !== false;
                const isChecked = isAllSelected || selectedFields.includes(field.key);

                return (
                  <label
                    key={field.key}
                    className={cn(
                      'flex select-none items-center justify-between gap-2 rounded-md px-2 py-1 text-xs transition-colors',
                      isSearchable
                        ? 'cursor-pointer hover:bg-main/5'
                        : 'cursor-not-allowed opacity-50',
                    )}
                  >
                    <div className="flex min-w-0 items-center gap-2">
                      <input
                        type="checkbox"
                        checked={isChecked}
                        disabled={!isSearchable}
                        onChange={() => isSearchable && toggleField(field.key)}
                className="accent-accent"
                      />
                      <span className="truncate text-main">{field.label}</span>
                    </div>

                    {!isSearchable && (
                      <Badge tone="muted" className="shrink-0 text-[10px]">
                        {t('search.notSupported')}
                      </Badge>
                    )}
                  </label>
                );
              })}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
