import { cn } from '../../lib/cn';

interface ToggleProps {
  checked: boolean;
  onChange: (next: boolean) => void;
  /** Text next to the switch; the whole row is clickable. */
  label: string;
  disabled?: boolean;
  className?: string;
}

/**
 * Accessible toggle switch (`role="switch"`) for boolean SETTINGS — a setting the
 * user turns on/off (account enabled, group active, all-permissions). Multi-select
 * lists (role/group/permission pickers) stay CheckboxList — a switch communicates
 * a single state, not selection. Track is h-5 (36px control rhythm); colors are
 * theme tokens only.
 */
export function Toggle({ checked, onChange, label, disabled, className }: ToggleProps) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      disabled={disabled}
      onClick={() => onChange(!checked)}
      className={cn(
        'inline-flex cursor-pointer items-center gap-2.5 text-sm text-main transition-colors',
        'focus:outline-none focus-visible:ring-2 focus-visible:ring-accent/60 rounded-md px-0.5 py-0.5',
        'disabled:cursor-not-allowed disabled:opacity-50',
        className,
      )}
    >
      <span
        aria-hidden
        className={cn(
          'relative inline-flex h-5 w-9 shrink-0 items-center rounded-full transition-colors duration-150',
          checked ? 'bg-accent' : 'bg-main/15',
        )}
      >
        <span
          className={cn(
            'absolute h-4 w-4 rounded-full bg-surface shadow-sm transition-transform duration-150',
            checked ? 'translate-x-[18px]' : 'translate-x-0.5',
          )}
        />
      </span>
      <span>{label}</span>
    </button>
  );
}
