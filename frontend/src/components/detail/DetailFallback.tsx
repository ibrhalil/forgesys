import { Link } from 'react-router-dom';

interface DetailLoadingProps {
  message: string;
}

interface DetailNotFoundProps {
  message: string;
  backLabel: string;
  backTo: string;
}

/**
 * Shared loading + not-found fallbacks for the detail pages. All detail pages used to
 * hand-roll the same two blocks; this centralizes the markup so the "loading spinner,
 * then not-found card" flow stays visually identical everywhere.
 */
export function DetailLoading({ message }: DetailLoadingProps) {
  return <div className="py-16 text-center text-sm text-muted">{message}</div>;
}

export function DetailNotFound({ message, backLabel, backTo }: DetailNotFoundProps) {
  return (
    <div className="flex flex-col items-center gap-3 py-16 text-center text-muted">
      <p>{message}</p>
      <Link to={backTo} className="text-accent hover:underline">{backLabel}</Link>
    </div>
  );
}