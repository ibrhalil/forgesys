import { cn } from '../../lib/cn';
import { useT } from '../../lib/i18n';

export interface CheckboxItem {
  id: string;
  label: string;
  description?: string | null;
}

interface CheckboxListProps {
  items: CheckboxItem[];
  selectedIds: string[];
  onChange: (ids: string[]) => void;
  emptyMessage?: string;
}

export function CheckboxList({ items, selectedIds, onChange, emptyMessage }: CheckboxListProps) {
  const { t } = useT();
  if (items.length === 0) {
    return <p className="py-6 text-center text-sm text-muted">{emptyMessage ?? t('common.nothingToSelect')}</p>;
  }

  const toggle = (id: string) => {
    onChange(selectedIds.includes(id) ? selectedIds.filter((x) => x !== id) : [...selectedIds, id]);
  };

  return (
    <ul className="flex max-h-72 flex-col gap-1 overflow-y-auto pr-1">
      {items.map((item) => {
        const checked = selectedIds.includes(item.id);
        return (
          <li key={item.id}>
            <label
              className={cn(
                'flex cursor-pointer items-start gap-3 rounded-lg border px-3 py-2 transition-colors',
                checked ? 'border-accent/40 bg-accent/10' : 'border-transparent hover:bg-main/5',
              )}
            >
              <input
                type="checkbox"
                checked={checked}
                onChange={() => toggle(item.id)}
                className="mt-0.5 h-4 w-4 accent-[var(--color-accent)]"
              />
              <span className="flex flex-col">
                <span className="text-sm font-medium text-main">{item.label}</span>
                {item.description && (
                  <span className="text-xs text-muted">{item.description}</span>
                )}
              </span>
            </label>
          </li>
        );
      })}
    </ul>
  );
}
