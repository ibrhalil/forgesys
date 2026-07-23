interface EmptyStateProps {
  message?: string;
  hint?: string;
}

export function EmptyState({ message = 'No data', hint }: EmptyStateProps) {
  return (
    <div className="flex flex-col items-center justify-center gap-2 py-16 text-center">
      <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" className="text-muted/50">
        <path d="M3 7v10a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2h-6l-2-3H5a2 2 0 0 0-2 2Z" />
      </svg>
      <p className="text-sm text-muted">{message}</p>
      {hint && <p className="text-xs text-muted/60">{hint}</p>}
    </div>
  );
}
